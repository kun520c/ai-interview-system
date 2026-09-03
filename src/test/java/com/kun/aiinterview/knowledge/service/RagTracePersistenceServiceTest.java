package com.kun.aiinterview.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.knowledge.entity.RagHitLog;
import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.RagHitLogMapper;
import com.kun.aiinterview.knowledge.mapper.RagRetrievalBatchMapper;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagTracePersistenceServiceTest {

    private static final String QUERY = "HashMap 扩容机制";
    private static final String EMBEDDING_MODEL = "test-embedding-model";
    private static final String EMBEDDING_VERSION = "test-profile-v1";
    private static final long ANSWER_ID = 1001L;

    @Mock
    private RagRetrievalBatchMapper ragRetrievalBatchMapper;

    @Mock
    private RagHitLogMapper ragHitLogMapper;

    @Captor
    private ArgumentCaptor<RagRetrievalBatch> batchCaptor;

    @Captor
    private ArgumentCaptor<List<RagHitLog>> hitsCaptor;

    private ObjectMapper objectMapper;
    private RagTracePersistenceService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RagTracePersistenceService(
                ragRetrievalBatchMapper,
                ragHitLogMapper
        );
    }

    @Test
    void shouldPersistBatchAndOnlyEligibleEvidenceWithOriginalRanks() throws Exception {
        String batchId = UUID.randomUUID().toString();
        EvaluationRetrievalResult result = retrievalResult(
                5,
                List.of(
                        evidence(1, 101L, 0.91D),
                        evidence(3, 103L, 0.81D),
                        evidence(5, 105L, 0.71D)
                )
        );
        when(ragRetrievalBatchMapper.insertBatch(any())).thenReturn(1);
        when(ragHitLogMapper.batchInsert(any())).thenReturn(3);

        service.persist(batchId, ANSWER_ID, result);

        InOrder order = inOrder(ragRetrievalBatchMapper, ragHitLogMapper);
        order.verify(ragRetrievalBatchMapper).insertBatch(batchCaptor.capture());
        order.verify(ragHitLogMapper).batchInsert(hitsCaptor.capture());

        RagRetrievalBatch batch = batchCaptor.getValue();
        assertThat(batch.getRetrievalBatchId()).isEqualTo(batchId);
        assertThat(batch.getAnswerId()).isEqualTo(ANSWER_ID);
        assertThat(batch.getQueryText()).isEqualTo(QUERY);
        assertThat(batch.getTopK()).isEqualTo(5);
        assertThat(batch.getEmbeddingModel()).isEqualTo(EMBEDDING_MODEL);
        assertThat(batch.getEmbeddingVersion()).isEqualTo(EMBEDDING_VERSION);
        assertThat(batch.getRawHitCount()).isEqualTo(5);
        assertThat(batch.getEvidenceHitCount()).isEqualTo(3);
        assertFilterSnapshot(batch.getFilterSnapshot());

        List<RagHitLog> hits = hitsCaptor.getValue();
        assertThat(hits).hasSize(3);
        assertThat(hits).extracting(RagHitLog::getRankNo)
                .containsExactly(1, 3, 5);
        assertThat(hits).extracting(RagHitLog::getChunkId)
                .containsExactly(101L, 103L, 105L);
        assertThat(hits).extracting(RagHitLog::getSimilarityScore)
                .containsExactly(0.91D, 0.81D, 0.71D);

        for (RagHitLog hit : hits) {
            assertThat(hit.getAnswerId()).isEqualTo(ANSWER_ID);
            assertThat(hit.getRetrievalBatchId()).isEqualTo(batchId);
            assertThat(hit.getQueryText()).isEqualTo(QUERY);
            assertThat(hit.getTopK()).isEqualTo(5);
            assertThat(hit.getEmbeddingModel()).isEqualTo(EMBEDDING_MODEL);
            assertThat(hit.getEmbeddingVersion()).isEqualTo(EMBEDDING_VERSION);
            assertThat(objectMapper.readTree(hit.getFilterSnapshot()))
                    .isEqualTo(objectMapper.readTree(batch.getFilterSnapshot()));
        }
    }

    @Test
    void shouldPersistZeroRawZeroEvidenceBatchWithoutCallingHitMapper() {
        EvaluationRetrievalResult result = retrievalResult(0, List.of());
        when(ragRetrievalBatchMapper.insertBatch(any())).thenReturn(1);

        service.persist(UUID.randomUUID().toString(), ANSWER_ID, result);

        verify(ragRetrievalBatchMapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getRawHitCount()).isZero();
        assertThat(batchCaptor.getValue().getEvidenceHitCount()).isZero();
        verifyNoInteractions(ragHitLogMapper);
    }

    @Test
    void shouldPersistRawHitsWithoutEvidenceAndWithoutCallingHitMapper() {
        EvaluationRetrievalResult result = retrievalResult(5, List.of());
        when(ragRetrievalBatchMapper.insertBatch(any())).thenReturn(1);

        service.persist(UUID.randomUUID().toString(), ANSWER_ID, result);

        verify(ragRetrievalBatchMapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getRawHitCount()).isEqualTo(5);
        assertThat(batchCaptor.getValue().getEvidenceHitCount()).isZero();
        verifyNoInteractions(ragHitLogMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldRejectUnexpectedBatchAffectedRows(int affectedRows) {
        when(ragRetrievalBatchMapper.insertBatch(any())).thenReturn(affectedRows);

        assertThatThrownBy(() -> service.persist(
                UUID.randomUUID().toString(),
                ANSWER_ID,
                retrievalResult(5, List.of(evidence(1, 101L, 0.9D)))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("RAG Retrieval Batch写入失败");

        verifyNoInteractions(ragHitLogMapper);
    }

    @Test
    void shouldRejectUnexpectedHitAffectedRows() {
        when(ragRetrievalBatchMapper.insertBatch(any())).thenReturn(1);
        when(ragHitLogMapper.batchInsert(any())).thenReturn(2);
        EvaluationRetrievalResult result = retrievalResult(
                5,
                List.of(
                        evidence(1, 101L, 0.91D),
                        evidence(3, 103L, 0.81D),
                        evidence(5, 105L, 0.71D)
                )
        );

        assertThatThrownBy(() -> service.persist(
                UUID.randomUUID().toString(),
                ANSWER_ID,
                result
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("RAG Hit Log写入数量异常");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingBatchIdWithoutMapperInteractions(String batchId) {
        assertThatThrownBy(() -> service.persist(
                batchId,
                ANSWER_ID,
                retrievalResult(0, List.of())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retrievalBatchId不能为空");

        verifyNoInteractions(ragRetrievalBatchMapper, ragHitLogMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void shouldRejectInvalidAnswerIdWithoutMapperInteractions(Long answerId) {
        assertThatThrownBy(() -> service.persist(
                UUID.randomUUID().toString(),
                answerId,
                retrievalResult(0, List.of())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("answerId必须大于0");

        verifyNoInteractions(ragRetrievalBatchMapper, ragHitLogMapper);
    }

    @Test
    void shouldRejectNullRetrievalResultWithoutMapperInteractions() {
        assertThatThrownBy(() -> service.persist(
                UUID.randomUUID().toString(),
                ANSWER_ID,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EvaluationRetrievalResult不能为空");

        verifyNoInteractions(ragRetrievalBatchMapper, ragHitLogMapper);
    }

    private EvaluationRetrievalResult retrievalResult(
            int rawHitCount,
            List<RetrievedChunk> evidence
    ) {
        List<VectorSearchHit> rawHits = java.util.stream.IntStream
                .range(0, rawHitCount)
                .mapToObj(index -> new VectorSearchHit(
                        "raw-vector-" + index,
                        200L + index,
                        index + 1,
                        EMBEDDING_VERSION,
                        0.99D - index * 0.01D
                ))
                .toList();
        return new EvaluationRetrievalResult(
                QUERY,
                5,
                EMBEDDING_MODEL,
                EMBEDDING_VERSION,
                rawHits,
                evidence
        );
    }

    private RetrievedChunk evidence(
            int rank,
            long chunkId,
            double similarityScore
    ) {
        return new RetrievedChunk(
                rank,
                similarityScore,
                chunkId,
                300L + rank,
                1,
                rank,
                "eligible-vector-" + rank,
                "HashMap 文档 " + rank,
                KnowledgeCategory.JAVA_COLLECTION,
                "test source",
                "HashMap evidence " + rank
        );
    }

    private void assertFilterSnapshot(String snapshot) throws Exception {
        JsonNode json = objectMapper.readTree(snapshot);
        assertThat(json.path("filterVersion").asText())
                .isEqualTo("evaluation-rag-filter-v1");
        assertThat(json.path("milvus")
                .path("requireEmbeddingVersionMatch").asBoolean()).isTrue();
        assertThat(json.path("mysql").path("chunkStatus").asText())
                .isEqualTo("ACTIVE");
        assertThat(json.path("mysql").path("documentProcessingStatus").asText())
                .isEqualTo("READY");
        assertThat(json.path("mysql")
                .path("requireCurrentDocumentVersion").asBoolean()).isTrue();
        assertThat(json.path("mysql")
                .path("requireEmbeddingModelMatch").asBoolean()).isTrue();
        assertThat(json.path("mysql")
                .path("requireEmbeddingVersionMatch").asBoolean()).isTrue();
        assertThat(json.has("category")).isFalse();
        assertThat(json.has("similarityThreshold")).isFalse();
        assertThat(json.has("rerank")).isFalse();
        assertThat(json.has("queryRewrite")).isFalse();
    }
}
