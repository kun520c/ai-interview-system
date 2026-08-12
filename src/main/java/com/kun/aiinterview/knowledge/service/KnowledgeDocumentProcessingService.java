package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.chunk.KnowledgeChunkDraft;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class KnowledgeDocumentProcessingService {
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeTextChunker knowledgeTextChunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final KnowledgeDocumentProcessingTransactionService knowledgeDocumentProcessingTransactionService;

    public void processDocument(Long documentId) {
        validateDocumentId(documentId);

        int claimedRows = knowledgeDocumentMapper.claimProcessing(documentId);

        if(claimedRows != 1){
            throw new BusinessException("文档不存在或当前不允许处理");
        }

        String failureMessage = "知识文档处理失败";

        boolean milvusWriteMayExist = false;

        List<String> vectorIds = new ArrayList<>();

        try{
            KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);

            validateClaimedDocument(document);

            failureMessage = "知识文档切片失败";

            List<KnowledgeChunkDraft> drafts = knowledgeTextChunker.split(
                    document.getContent()
            );

            List<String> texts = drafts.stream()
                    .map(KnowledgeChunkDraft::content)
                    .toList();

            failureMessage = "知识文档Embedding生成失败";

            EmbeddingBatchResult embeddingResult = embeddingClient.embed(texts);

            List<EmbeddingVector> orderedVectors = orderEmbeddingVectors(
                    embeddingResult,
                    drafts.size()
            );

            failureMessage = "知识文档向量数据组装失败";

            ProcessingItems processingItems =
                        buildProcessingItems(
                                document,
                                drafts,
                                embeddingResult,
                                orderedVectors,
                                vectorIds
                        );

            failureMessage = "Milvus向量写入失败";

            milvusWriteMayExist = true;

            vectorStoreClient.insert(processingItems.vectorItems());

            failureMessage = "知识切片持久化或文档状态更新失败";

            knowledgeDocumentProcessingTransactionService
                    .persistChunksAndMarkReady(
                            documentId,
                            processingItems.chunks()
                    );
        }catch (RuntimeException originalException){
            if(milvusWriteMayExist && !vectorIds.isEmpty()){
                compensateMilvus(
                        documentId,
                        vectorIds,
                        originalException
                );
            }

            markDocumentFailed(
                    documentId,
                    failureMessage,
                    originalException
            );

            throw originalException;
        }
    }

    private ProcessingItems buildProcessingItems(
            KnowledgeDocument document,
            List<KnowledgeChunkDraft> drafts,
            EmbeddingBatchResult embeddingResult,
            List<EmbeddingVector> orderedVectors,
            List<String> vectorIds
    ){
        List<KnowledgeChunk> chunks = new ArrayList<>(drafts.size());

        List<VectorWriteItem> vectorItems = new ArrayList<>(drafts.size());

        for(int index = 0;index < drafts.size();index++){
            KnowledgeChunkDraft draft = drafts.get(index);

            EmbeddingVector embeddingVector = orderedVectors.get(index);

            String vectorId = UUID.randomUUID().toString();

            vectorIds.add(vectorId);

            KnowledgeChunk chunk =
                    KnowledgeChunk.builder()
                            .documentId(document.getId())
                            .documentVersion(
                                    document.getDocumentVersion()
                            )
                            .chunkIndex(draft.chunkIndex())
                            .content(draft.content())
                            .tokenCount(null)
                            .vectorId(vectorId)
                            .embeddingModel(
                                        embeddingResult.model()
                            )
                            .embeddingVersion(
                                        embeddingResult.profileVersion()
                            )
                            .status(
                                        KnowledgeChunkStatus.ACTIVE
                            )
                            .build();

            VectorWriteItem vectorWriteItem =
                    new VectorWriteItem(
                            vectorId,
                            document.getId(),
                            draft.chunkIndex(),
                            embeddingResult.profileVersion(),
                            embeddingVector.values()
                    );

            chunks.add(chunk);
            vectorItems.add(vectorWriteItem);
        }

        return new ProcessingItems(
                List.copyOf(chunks),
                List.copyOf(vectorItems)
        );
    }

    private List<EmbeddingVector> orderEmbeddingVectors(
            EmbeddingBatchResult embeddingResult,
            int expectedCount
    ){
        if(embeddingResult == null){
            throw new BusinessException(
                    "Embedding返回结果不能为空"
            );
        }

        List<EmbeddingVector> vectors = embeddingResult.vectors();

        if(vectors.size() != expectedCount){
            throw new BusinessException(
                    "Embedding返回向量数量与知识切片数量不一致"
            );
        }

        List<EmbeddingVector> ordered =
                new ArrayList<>(
                        java.util.Collections.nCopies(
                                expectedCount,
                                null
                        )
                );

        for(EmbeddingVector vector : vectors){
            int inputIndex = vector.inputIndex();

            if(inputIndex < 0 || inputIndex >= expectedCount){
                throw new BusinessException(
                        "Embedding返回了非法输入索引："
                            +inputIndex
                );
            }

            if(ordered.get(inputIndex) != null){
                throw new BusinessException(
                        "Embedding返回了重复输入索引："
                            +inputIndex
                );
            }

            ordered.set(inputIndex, vector);
        }
        for(int index = 0;index < ordered.size();index++){
            if(ordered.get(index) == null){
                throw new BusinessException(
                        "Embedding缺少输入索引："
                                + index
                );
            }
        }

        return List.copyOf(ordered);
    }

    private void compensateMilvus(
            Long documentId,
            List<String> vectorIds,
            RuntimeException originalException
    ){
        try {
            vectorStoreClient.deleteByVectorIds(
                    List.copyOf(vectorIds)
            );
        }catch (RuntimeException compensationException){
            log.error(
                    "Milvus compensation failed,documentId = {}",
                    documentId,
                    compensationException
            );

            originalException.addSuppressed(compensationException);
        }
    }

    private void markDocumentFailed(
            Long documentId,
            String failureMessage,
            RuntimeException originalException
    ){
        try {
            int updatedRows =
                        knowledgeDocumentMapper.markFailed(
                                documentId,
                                failureMessage
                        );

            if(updatedRows != 1){
                IllegalArgumentException stateException =
                        new IllegalArgumentException(
                                "知识文档FAILED状态更新失败"
                        );

                originalException.addSuppressed(
                        stateException
                );

                log.error(
                        "Failed to mark document as FAILED,documentId = {}",
                        documentId
                );
            }
        }catch (RuntimeException statusException){
            originalException.addSuppressed(statusException);

            log.error(
                    "Exception while marking document as FAILED,documentId = {}",
                    documentId,
                    statusException
            );
        }
    }

    private void validateDocumentId(Long documentId){
        if(documentId == null || documentId <= 0){
            throw new BusinessException("文档ID必须大于0");
        }
    }

    private void validateClaimedDocument(KnowledgeDocument document) {
        if (document == null) {
            throw new BusinessException(
                    "已抢占处理状态但未查询到知识文档"
            );
        }

        if (document.getId() == null
                || document.getId() <= 0) {
            throw new BusinessException(
                    "知识文档ID不合法"
            );
        }

        if (document.getDocumentVersion() == null
                || document.getDocumentVersion() <= 0) {
            throw new BusinessException(
                    "知识文档版本不合法"
            );
        }

        if (document.getProcessingStatus()
                != KnowledgeProcessingStatus.PROCESSING) {
            throw new BusinessException(
                    "知识文档状态与处理流程不一致"
            );
        }
    }

    private record ProcessingItems(
            List<KnowledgeChunk> chunks,
            List<VectorWriteItem> vectorItems
    ) {
    }
}
