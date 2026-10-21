package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
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
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
    private final StaleRecoveryProperties staleRecoveryProperties;

    public void processDocument(Long documentId) {
        validateDocumentId(documentId);

        KnowledgeDocument occupied = occupyProcessing(documentId);

        String failureMessage = "知识文档处理失败";

        boolean milvusWriteMayExist = false;
        boolean mysqlCommitAttempted = false;

        List<String> vectorIds = new ArrayList<>();

        List<String> previousVectorIds;
        try {

            failureMessage = "知识文档切片失败";

            List<KnowledgeChunkDraft> drafts = knowledgeTextChunker.split(
                    occupied.getContent()
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
                                occupied,
                                drafts,
                                embeddingResult,
                                orderedVectors,
                                vectorIds
                        );

            failureMessage = "Milvus向量写入失败";

            milvusWriteMayExist = true;

            vectorStoreClient.insert(processingItems.vectorItems());

            failureMessage = "知识切片持久化或文档状态更新失败";

            mysqlCommitAttempted = true;
            previousVectorIds = knowledgeDocumentProcessingTransactionService
                    .persistChunksAndMarkReady(
                            documentId,
                            processingItems.chunks(),
                            occupied.getProcessingAttemptId()
                    );
        } catch (RuntimeException originalException) {
            boolean mayCompensate = milvusWriteMayExist;
            if (mysqlCommitAttempted) {
                try {
                    // A current locking read waits for any unresolved commit holding this row.
                    // A snapshot read could still see PROCESSING and wrongly compensate a commit in flight.
                    KnowledgeDocument current = knowledgeDocumentMapper.lockById(documentId);
                    if (current != null
                            && current.getProcessingStatus() == KnowledgeProcessingStatus.READY
                            && occupied.getProcessingAttemptId().equals(current.getProcessingAttemptId())) {
                        // A lost commit acknowledgement must not delete this committed batch.
                        log.warn("Processing commit confirmed after transaction error, documentId = {}, attemptId = {}",
                                documentId, occupied.getProcessingAttemptId(), originalException);
                        return;
                    }
                } catch (RuntimeException confirmationFailure) {
                    originalException.addSuppressed(confirmationFailure);
                    mayCompensate = false;
                    log.error("Cannot confirm MySQL commit; retaining vectors, documentId = {}, vectorIds = {}",
                            documentId, vectorIds, confirmationFailure);
                }
            }

            if (mayCompensate && !vectorIds.isEmpty()) {
                compensateMilvus(
                        documentId,
                        vectorIds,
                        originalException
                );
            }

            markDocumentFailed(
                    documentId,
                    occupied.getProcessingAttemptId(),
                    failureMessage,
                    originalException
            );

            throw originalException;
        }
        // MySQL is already committed. Cleanup failure must never compensate the active batch.
        if (!previousVectorIds.isEmpty()) {
            try {
                vectorStoreClient.deleteByVectorIds(previousVectorIds);
            } catch (RuntimeException cleanupFailure) {
                log.error("Retired vector cleanup failed, documentId = {}, vectorIds = {}",
                        documentId, previousVectorIds, cleanupFailure);
            }
        }
    }

    private KnowledgeDocument occupyProcessing(Long documentId) {
        KnowledgeDocument document =
                knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new ResourceNotFoundException("知识文档不存在");
        }

        String attemptId = UUID.randomUUID().toString().replace("-", "");
        KnowledgeProcessingStatus status =
                document.getProcessingStatus();
        if (status == KnowledgeProcessingStatus.READY) {
            throw new ConflictException(
                    "文档已处理完成，不能重复处理"
            );
        }
        if (status == KnowledgeProcessingStatus.PROCESSING) {
            occupyStaleProcessing(document, attemptId);
        } else if (status == KnowledgeProcessingStatus.FAILED) {
            occupyFailedProcessing(documentId, attemptId);
        } else if (status == KnowledgeProcessingStatus.UPLOADED) {
            occupyUploaded(documentId, attemptId);
        } else {
            throw new ConflictException(
                    "文档当前状态不允许处理"
            );
        }

        // Never adopt a later worker's identity by reloading after the claim.
        document.setProcessingStatus(KnowledgeProcessingStatus.PROCESSING);
        document.setProcessingAttemptId(attemptId);
        validateClaimedDocument(document);
        return document;
    }

    private void occupyUploaded(Long documentId, String attemptId) {
        int claimedRows =
                knowledgeDocumentMapper.claimProcessing(documentId, attemptId);
        if (claimedRows == 1) {
            return;
        }
        rejectLostProcessingRace(documentId);
    }

    private void occupyFailedProcessing(Long documentId, String attemptId) {
        int claimedRows =
                knowledgeDocumentMapper.claimFailedProcessing(documentId, attemptId);
        if (claimedRows == 1) {
            return;
        }
        rejectLostProcessingRace(documentId);
    }

    private void occupyStaleProcessing(KnowledgeDocument document, String attemptId) {
        if (!staleRecoveryProperties
                .isProcessingStale(document.getUpdatedAt())) {
            throw new ConflictException(
                    "文档正在处理中，请稍后重试"
            );
        }

        int reclaimed =
                knowledgeDocumentMapper.reclaimStaleProcessing(
                        document.getId(),
                        staleRecoveryProperties.processingCutoff(),
                        attemptId
                );
        if (reclaimed == 1) {
            return;
        }
        rejectLostProcessingRace(document.getId());
    }

    private void rejectLostProcessingRace(Long documentId) {
        KnowledgeDocument latest =
                knowledgeDocumentMapper.selectById(documentId);
        if (latest == null) {
            throw new ResourceNotFoundException("知识文档不存在");
        }
        if (latest.getProcessingStatus()
                == KnowledgeProcessingStatus.READY) {
            throw new ConflictException(
                    "文档已处理完成，不能重复处理"
            );
        }
        throw new ConflictException(
                "文档正在处理中，请稍后重试"
        );
    }

    private ProcessingItems buildProcessingItems(
            KnowledgeDocument document,
            List<KnowledgeChunkDraft> drafts,
            EmbeddingBatchResult embeddingResult,
            List<EmbeddingVector> orderedVectors,
            List<String> vectorIds
    ) {
        List<KnowledgeChunk> chunks = new ArrayList<>(drafts.size());

        List<VectorWriteItem> vectorItems = new ArrayList<>(drafts.size());

        for (int index = 0;index < drafts.size();index++) {
            KnowledgeChunkDraft draft = drafts.get(index);

            EmbeddingVector embeddingVector = orderedVectors.get(index);

            String vectorId = document.getProcessingAttemptId() + "-" + draft.chunkIndex();

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
    ) {
        if (embeddingResult == null) {
            throw new BusinessException(
                    "Embedding返回结果不能为空"
            );
        }

        List<EmbeddingVector> vectors = embeddingResult.vectors();

        if (vectors.size() != expectedCount) {
            throw new BusinessException(
                    "Embedding返回向量数量与知识切片数量不一致"
            );
        }

        List<EmbeddingVector> ordered =
                new ArrayList<>(
                        Collections.nCopies(
                                expectedCount,
                                null
                        )
                );

        for (EmbeddingVector vector : vectors) {
            int inputIndex = vector.inputIndex();

            if (inputIndex < 0 || inputIndex >= expectedCount) {
                throw new BusinessException(
                        "Embedding返回了非法输入索引："
                            +inputIndex
                );
            }

            if (ordered.get(inputIndex) != null) {
                throw new BusinessException(
                        "Embedding返回了重复输入索引："
                            +inputIndex
                );
            }

            ordered.set(inputIndex, vector);
        }

        for (int index = 0;index < ordered.size();index++) {
            if (ordered.get(index) == null) {
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
    ) {
        try {
            vectorStoreClient.deleteByVectorIds(
                    List.copyOf(vectorIds)
            );
        } catch (RuntimeException compensationException) {
            log.error(
                    "Milvus compensation failed, documentId = {}, vectorIds = {}",
                    documentId,
                    vectorIds,
                    compensationException
            );

            originalException.addSuppressed(compensationException);
        }
    }

    private void markDocumentFailed(
            Long documentId,
            String attemptId,
            String failureMessage,
            RuntimeException originalException
    ) {
        try {
            int updatedRows =
                        knowledgeDocumentMapper.markFailed(
                                documentId,
                                failureMessage,
                                attemptId
                        );

            if (updatedRows != 1) {
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
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);

            log.error(
                    "Exception while marking document as FAILED,documentId = {}",
                    documentId,
                    statusException
            );
        }
    }

    private void validateDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
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
