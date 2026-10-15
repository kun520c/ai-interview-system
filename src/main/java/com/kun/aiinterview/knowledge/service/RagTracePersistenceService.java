package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.knowledge.entity.RagHitLog;
import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import com.kun.aiinterview.knowledge.mapper.RagHitLogMapper;
import com.kun.aiinterview.knowledge.mapper.RagRetrievalBatchMapper;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.retrieval.RetrievalProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RagTracePersistenceService {

    private static final String FILTER_SNAPSHOT_V1 = """
            {
              "filterVersion": "evaluation-rag-filter-v1",
              "milvus": {
                "requireEmbeddingVersionMatch": true
              },
              "mysql": {
                "chunkStatus": "ACTIVE",
                "documentProcessingStatus": "READY",
                "requireCurrentDocumentVersion": true,
                "requireEmbeddingModelMatch": true,
                "requireEmbeddingVersionMatch": true
              }
            }
            """;

    private static final String FILTER_SNAPSHOT_V2 = """
            {
              "filterVersion": "evaluation-rag-filter-v2",
              "cosineCandidate": {
                "enabled": true,
                "metric": "COSINE",
                "minScore": %s,
                "scorePrecision": "FLOAT32"
              },
              "milvus": {
                "requireEmbeddingVersionMatch": true
              },
              "mysql": {
                "chunkStatus": "ACTIVE",
                "documentProcessingStatus": "READY",
                "requireCurrentDocumentVersion": true,
                "requireEmbeddingModelMatch": true,
                "requireEmbeddingVersionMatch": true
              }
            }
            """;

    private final RagRetrievalBatchMapper ragRetrievalBatchMapper;
    private final RagHitLogMapper ragHitLogMapper;
    private final RetrievalProperties retrievalProperties;
    private final InterviewAnswerMapper interviewAnswerMapper;

    public RagTracePersistenceService(
            RagRetrievalBatchMapper ragRetrievalBatchMapper,
            RagHitLogMapper ragHitLogMapper,
            RetrievalProperties retrievalProperties,
            InterviewAnswerMapper interviewAnswerMapper
    ) {
        this.ragRetrievalBatchMapper =
                ragRetrievalBatchMapper;

        this.ragHitLogMapper =
                ragHitLogMapper;
        this.retrievalProperties = retrievalProperties;
        this.interviewAnswerMapper = interviewAnswerMapper;
    }

    @Transactional
    public void persistFailedAttempt(String batchId, Long answerId,
            EvaluationRetrievalResult retrieval, String attemptId) {
        InterviewAnswer current = interviewAnswerMapper.lockById(answerId);
        if (attemptId == null || current == null
                || current.getStatus() != InterviewAnswerStatus.EVALUATING
                || !attemptId.equals(current.getAttemptId())) {
            return;
        }
        persist(batchId, answerId, retrieval);
    }

    @Transactional
    public void persist(
            String retrievalBatchId,
            Long answerId,
            EvaluationRetrievalResult retrievalResult
    ) {
        validateInput(
                retrievalBatchId,
                answerId,
                retrievalResult
        );

        RagRetrievalBatch batch =
                buildBatch(
                        retrievalBatchId,
                        answerId,
                        retrievalResult
                );

        int batchAffectedRows =
                ragRetrievalBatchMapper
                        .insertBatch(batch);

        if (batchAffectedRows != 1) {
            throw new IllegalStateException(
                    "RAG Retrieval Batch写入失败"
            );
        }

        if (retrievalResult.evidence().isEmpty()) {
            return;
        }

        List<RagHitLog> hits =
                buildHits(
                        retrievalBatchId,
                        answerId,
                        retrievalResult
                );

        int hitAffectedRows =
                ragHitLogMapper
                        .batchInsert(hits);

        if (hitAffectedRows != hits.size()) {
            throw new IllegalStateException(
                    "RAG Hit Log写入数量异常"
            );
        }
    }

    private RagRetrievalBatch buildBatch(
            String retrievalBatchId,
            Long answerId,
            EvaluationRetrievalResult retrievalResult
    ) {

        return RagRetrievalBatch.builder()

                .retrievalBatchId(
                        retrievalBatchId
                )

                .answerId(
                        answerId
                )

                .queryText(
                        retrievalResult.query()
                )

                .topK(
                        retrievalResult.requestedTopK()
                )

                .filterSnapshot(
                        filterSnapshot()
                )

                .embeddingModel(
                        retrievalResult.embeddingModel()
                )

                .embeddingVersion(
                        retrievalResult.embeddingVersion()
                )

                .rawHitCount(
                        retrievalResult.rawHits().size()
                )

                .evidenceHitCount(
                        retrievalResult.evidence().size()
                )

                .build();
    }

    private List<RagHitLog> buildHits(
            String retrievalBatchId,
            Long answerId,
            EvaluationRetrievalResult retrievalResult
    ) {

        return retrievalResult
                .evidence()
                .stream()
                .map(
                        evidence ->
                                buildHit(
                                        retrievalBatchId,
                                        answerId,
                                        retrievalResult,
                                        evidence
                                )
                )
                .toList();
    }

    private RagHitLog buildHit(
            String retrievalBatchId,
            Long answerId,
            EvaluationRetrievalResult retrievalResult,
            RetrievedChunk evidence
    ) {
        return RagHitLog.builder()

                .answerId(
                        answerId
                )

                .retrievalBatchId(
                        retrievalBatchId
                )

                .chunkId(
                        evidence.chunkId()
                )

                .similarityScore(
                        evidence.similarityScore()
                )

                .rankNo(
                        evidence.vectorRank()
                )

                .queryText(
                        retrievalResult.query()
                )

                .topK(
                        retrievalResult.requestedTopK()
                )

                .filterSnapshot(
                        filterSnapshot()
                )

                .embeddingModel(
                        retrievalResult.embeddingModel()
                )

                .embeddingVersion(
                        retrievalResult.embeddingVersion()
                )

                .build();
    }

    private void validateInput(
            String retrievalBatchId,
            Long answerId,
            EvaluationRetrievalResult retrievalResult
    ) {

        if (retrievalBatchId == null
                ||retrievalBatchId.isBlank()) {

            throw new IllegalArgumentException(
                    "retrievalBatchId不能为空"
            );
        }

        if (answerId == null || answerId <= 0) {

            throw new IllegalArgumentException(
                    "answerId必须大于0"
            );
        }

        if (retrievalResult == null) {

            throw new IllegalArgumentException(
                    "EvaluationRetrievalResult不能为空"
            );
        }
    }

    private String filterSnapshot() {
        if (!retrievalProperties.isCosineThresholdEnabled()) {
            return FILTER_SNAPSHOT_V1;
        }
        return FILTER_SNAPSHOT_V2.formatted(
                Float.toString(retrievalProperties.getMinCosineScore())
        );
    }
}
