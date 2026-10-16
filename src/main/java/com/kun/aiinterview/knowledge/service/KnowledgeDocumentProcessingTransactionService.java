package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentProcessingTransactionService {
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Transactional
    public List<String> persistChunksAndMarkReady(
            Long documentId,
            List<KnowledgeChunk> chunks,
            String attemptId
    ) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("文档ID必须大于0");
        }

        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("待持久化知识切片不能为空");
        }

        var document = knowledgeDocumentMapper.lockById(documentId);
        if (attemptId == null || document == null
                || document.getProcessingStatus() != KnowledgeProcessingStatus.PROCESSING
                || !attemptId.equals(document.getProcessingAttemptId())) {
            throw new ConflictException("文档处理任务已失效，请重试");
        }
        for (KnowledgeChunk chunk : chunks) {
            if (chunk == null || !documentId.equals(chunk.getDocumentId())
                    || !document.getDocumentVersion().equals(chunk.getDocumentVersion())
                    || !(attemptId + "-" + chunk.getChunkIndex()).equals(chunk.getVectorId())) {
                throw new IllegalArgumentException("知识切片不属于当前处理批次");
            }
        }
        List<String> previousVectorIds = knowledgeChunkMapper.listVectorIdsByDocumentIdAndVersion(
                documentId, document.getDocumentVersion());
        knowledgeChunkMapper.deleteByDocumentIdAndVersion(documentId, document.getDocumentVersion());
        int insertedRows = knowledgeChunkMapper.batchInsert(chunks);

        if (insertedRows != chunks.size()) {
            throw new BusinessException(
                    "知识切片持久化数量异常，期望写入："
                        + chunks.size()
                        +",实际写入："
                        + insertedRows
            );
        }

        int updatedRows = knowledgeDocumentMapper.markReady(documentId, attemptId);

        if (updatedRows != 1) {
            throw new BusinessException("知识文档READY状态更新失败");
        }
        return List.copyOf(previousVectorIds);
    }
}
