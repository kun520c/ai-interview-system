package com.kun.aiinterview.knowledge.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.knowledge.entity.RagHitLog;
import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class RagTraceMapperTest {

    private static final String QUERY_TEXT = "HashMap 的扩容机制";
    private static final String EMBEDDING_MODEL = "test-embedding-model";
    private static final String EMBEDDING_VERSION = "test-profile-v1";
    private static final String FILTER_SNAPSHOT = """
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

    @Autowired
    private RagRetrievalBatchMapper ragRetrievalBatchMapper;

    @Autowired
    private RagHitLogMapper ragHitLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldInsertAndSelectCompleteRetrievalBatch() throws Exception {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch batch = batch(answerId, 5, 5, 3);

        int affectedRows = ragRetrievalBatchMapper.insertBatch(batch);
        RagRetrievalBatch found = ragRetrievalBatchMapper.getByBatchId(
                batch.getRetrievalBatchId()
        );

        assertThat(affectedRows).isEqualTo(1);
        assertThat(found).isNotNull();
        assertThat(found.getRetrievalBatchId()).isEqualTo(batch.getRetrievalBatchId());
        assertThat(found.getAnswerId()).isEqualTo(answerId);
        assertThat(found.getQueryText()).isEqualTo(QUERY_TEXT);
        assertThat(found.getTopK()).isEqualTo(5);
        assertThat(objectMapper.readTree(found.getFilterSnapshot()))
                .isEqualTo(objectMapper.readTree(FILTER_SNAPSHOT));
        assertThat(found.getEmbeddingModel()).isEqualTo(EMBEDDING_MODEL);
        assertThat(found.getEmbeddingVersion()).isEqualTo(EMBEDDING_VERSION);
        assertThat(found.getRawHitCount()).isEqualTo(5);
        assertThat(found.getEvidenceHitCount()).isEqualTo(3);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistZeroRawAndZeroEvidenceBatchWithoutHitRows() {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch batch = batch(answerId, 5, 0, 0);

        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);

        RagRetrievalBatch found = ragRetrievalBatchMapper.getByBatchId(
                batch.getRetrievalBatchId()
        );
        assertThat(found.getRawHitCount()).isZero();
        assertThat(found.getEvidenceHitCount()).isZero();
        assertThat(ragHitLogMapper.listByBatchId(batch.getRetrievalBatchId()))
                .isEmpty();
    }

    @Test
    void shouldPersistRawHitsWithoutEligibleEvidenceAndWithoutHitRows() {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch batch = batch(answerId, 5, 5, 0);

        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);

        RagRetrievalBatch found = ragRetrievalBatchMapper.getByBatchId(
                batch.getRetrievalBatchId()
        );
        assertThat(found.getRawHitCount()).isEqualTo(5);
        assertThat(found.getEvidenceHitCount()).isZero();
        assertThat(ragHitLogMapper.listByBatchId(batch.getRetrievalBatchId()))
                .isEmpty();
    }

    @Test
    void shouldBatchInsertAndSelectEvidenceHitsInOriginalRankOrder() throws Exception {
        long answerId = insertAnswerFixture();
        List<Long> chunkIds = insertKnowledgeChunks(3);
        RagRetrievalBatch batch = batch(answerId, 5, 5, 3);
        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);
        List<RagHitLog> hits = List.of(
                hit(answerId, batch.getRetrievalBatchId(), chunkIds.get(0), 1, 0.876543219D),
                hit(answerId, batch.getRetrievalBatchId(), chunkIds.get(1), 3, 0.765432109D),
                hit(answerId, batch.getRetrievalBatchId(), chunkIds.get(2), 5, 0.654321098D)
        );

        int affectedRows = ragHitLogMapper.batchInsert(hits);
        List<RagHitLog> found = ragHitLogMapper.listByBatchId(
                batch.getRetrievalBatchId()
        );

        assertThat(affectedRows).isEqualTo(3);
        assertThat(found).hasSize(3);
        assertThat(found).extracting(RagHitLog::getRankNo)
                .containsExactly(1, 3, 5);

        RagHitLog first = found.getFirst();
        assertThat(first.getId()).isNotNull();
        assertThat(first.getAnswerId()).isEqualTo(answerId);
        assertThat(first.getRetrievalBatchId()).isEqualTo(batch.getRetrievalBatchId());
        assertThat(first.getChunkId()).isEqualTo(chunkIds.getFirst());
        assertThat(first.getSimilarityScore()).isCloseTo(0.87654322D,
                org.assertj.core.data.Offset.offset(0.000000001D));
        assertThat(first.getRankNo()).isEqualTo(1);
        assertThat(first.getQueryText()).isEqualTo(QUERY_TEXT);
        assertThat(first.getTopK()).isEqualTo(5);
        assertThat(objectMapper.readTree(first.getFilterSnapshot()))
                .isEqualTo(objectMapper.readTree(FILTER_SNAPSHOT));
        assertThat(first.getEmbeddingModel()).isEqualTo(EMBEDDING_MODEL);
        assertThat(first.getEmbeddingVersion()).isEqualTo(EMBEDDING_VERSION);
        assertThat(first.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldRejectHitWhenRetrievalBatchDoesNotExist() {
        long answerId = insertAnswerFixture();
        long chunkId = insertKnowledgeChunks(1).getFirst();
        RagHitLog orphan = hit(
                answerId,
                UUID.randomUUID().toString(),
                chunkId,
                1,
                0.8D
        );

        assertThatThrownBy(() -> ragHitLogMapper.batchInsert(List.of(orphan)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectHitWhenKnowledgeChunkDoesNotExist() {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch batch = batch(answerId, 5, 1, 1);
        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);
        RagHitLog invalid = hit(
                answerId,
                batch.getRetrievalBatchId(),
                Long.MAX_VALUE,
                1,
                0.8D
        );

        assertThatThrownBy(() -> ragHitLogMapper.batchInsert(List.of(invalid)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectBatchWhenAnswerDoesNotExist() {
        RagRetrievalBatch invalid = batch(Long.MAX_VALUE, 5, 0, 0);

        assertThatThrownBy(() -> ragRetrievalBatchMapper.insertBatch(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateRankWithinSameBatch() {
        long answerId = insertAnswerFixture();
        List<Long> chunkIds = insertKnowledgeChunks(2);
        RagRetrievalBatch batch = batch(answerId, 5, 2, 2);
        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);
        List<RagHitLog> duplicateRankHits = List.of(
                hit(answerId, batch.getRetrievalBatchId(), chunkIds.get(0), 1, 0.9D),
                hit(answerId, batch.getRetrievalBatchId(), chunkIds.get(1), 1, 0.8D)
        );

        assertThatThrownBy(() -> ragHitLogMapper.batchInsert(duplicateRankHits))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectBatchWhenRawHitCountExceedsTopK() {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch invalid = batch(answerId, 5, 6, 0);

        assertThatThrownBy(() -> ragRetrievalBatchMapper.insertBatch(invalid))
                .isInstanceOf(UncategorizedSQLException.class);
    }

    @Test
    void shouldRejectBatchWhenEvidenceHitCountExceedsRawHitCount() {
        long answerId = insertAnswerFixture();
        RagRetrievalBatch invalid = batch(answerId, 5, 2, 3);

        assertThatThrownBy(() -> ragRetrievalBatchMapper.insertBatch(invalid))
                .isInstanceOf(UncategorizedSQLException.class);
    }

    @Test
    void shouldRejectHitWhenRankExceedsTopK() {
        long answerId = insertAnswerFixture();
        long chunkId = insertKnowledgeChunks(1).getFirst();
        RagRetrievalBatch batch = batch(answerId, 5, 1, 1);
        assertThat(ragRetrievalBatchMapper.insertBatch(batch)).isEqualTo(1);
        RagHitLog invalid = hit(
                answerId,
                batch.getRetrievalBatchId(),
                chunkId,
                6,
                0.8D
        );

        assertThatThrownBy(() -> ragHitLogMapper.batchInsert(List.of(invalid)))
                .isInstanceOf(UncategorizedSQLException.class);
    }

    private RagRetrievalBatch batch(
            long answerId,
            int topK,
            int rawHitCount,
            int evidenceHitCount
    ) {
        return RagRetrievalBatch.builder()
                .retrievalBatchId(UUID.randomUUID().toString())
                .answerId(answerId)
                .queryText(QUERY_TEXT)
                .topK(topK)
                .filterSnapshot(FILTER_SNAPSHOT)
                .embeddingModel(EMBEDDING_MODEL)
                .embeddingVersion(EMBEDDING_VERSION)
                .rawHitCount(rawHitCount)
                .evidenceHitCount(evidenceHitCount)
                .build();
    }

    private RagHitLog hit(
            long answerId,
            String retrievalBatchId,
            long chunkId,
            int rankNo,
            double similarityScore
    ) {
        return RagHitLog.builder()
                .answerId(answerId)
                .retrievalBatchId(retrievalBatchId)
                .chunkId(chunkId)
                .similarityScore(similarityScore)
                .rankNo(rankNo)
                .queryText(QUERY_TEXT)
                .topK(5)
                .filterSnapshot(FILTER_SNAPSHOT)
                .embeddingModel(EMBEDDING_MODEL)
                .embeddingVersion(EMBEDDING_VERSION)
                .build();
    }

    private long insertAnswerFixture() {
        String unique = uniqueValue();
        String account = "f4a-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, 'F4-A测试用户', 'test-password-hash', ?, 'USER', 'ENABLED')
                """,
                account,
                account + "@example.com"
        );
        long userId = requiredId("SELECT id FROM `user` WHERE account = ?", account);

        String knowledgePoint = "F4-A-RAG-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', ?, ?, 'ENABLED')
                """,
                knowledgePoint,
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案"
        );
        long questionId = requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status)
                VALUES (?, 'MEDIUM', 'CREATED', 1, 0, 'NOT_STARTED')
                """,
                userId
        );
        long sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        NULL, NULL, 1, 1, 'ANSWERED')
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "本场面试参考答案快照",
                "[{\"scoringPointId\":101,\"weight\":100}]"
        );
        long interviewQuestionId = requiredId(
                "SELECT id FROM interview_question WHERE session_id = ?",
                sessionId
        );

        String requestId = "f4a-answer-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, 'EVALUATED', ?)
                """,
                interviewQuestionId,
                "HashMap 使用数组、链表和红黑树。",
                requestId
        );
        return requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    private List<Long> insertKnowledgeChunks(int count) {
        String unique = uniqueValue();
        String title = "F4-A RAG trace " + unique;
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_document
                    (title, category, file_name, file_type, content, source,
                     content_hash, document_version, processing_status, error_message)
                VALUES (?, 'JAVA_COLLECTION', ?, 'MARKDOWN', ?, ?, ?, 1, 'READY', NULL)
                """,
                title,
                "f4a-" + unique + ".md",
                "# F4-A fixture\n" + unique,
                "F4-A Mapper Test",
                uniqueHash()
        );
        long documentId = requiredId(
                "SELECT id FROM knowledge_document WHERE title = ?",
                title
        );

        List<Long> chunkIds = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String vectorId = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    """
                    INSERT INTO knowledge_chunk
                        (document_id, document_version, chunk_index, content,
                         token_count, vector_id, embedding_model,
                         embedding_version, status)
                    VALUES (?, 1, ?, ?, NULL, ?, ?, ?, 'ACTIVE')
                    """,
                    documentId,
                    index,
                    "F4-A knowledge chunk " + index,
                    vectorId,
                    EMBEDDING_MODEL,
                    EMBEDDING_VERSION
            );
            chunkIds.add(requiredId(
                    "SELECT id FROM knowledge_chunk WHERE vector_id = ?",
                    vectorId
            ));
        }
        return chunkIds;
    }

    private long requiredId(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertThat(id).isNotNull();
        return id;
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String uniqueHash() {
        return uniqueValue() + uniqueValue();
    }
}
