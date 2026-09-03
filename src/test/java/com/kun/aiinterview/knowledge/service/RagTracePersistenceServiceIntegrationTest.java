package com.kun.aiinterview.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.knowledge.entity.RagHitLog;
import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.RagHitLogMapper;
import com.kun.aiinterview.knowledge.mapper.RagRetrievalBatchMapper;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class RagTracePersistenceServiceIntegrationTest {

    private static final String QUERY = "HashMap 扩容机制";
    private static final String EMBEDDING_MODEL = "f4b-test-embedding-model";
    private static final String EMBEDDING_VERSION = "f4b-test-profile-v1";

    @Autowired
    private RagTracePersistenceService ragTracePersistenceService;

    @Autowired
    private RagRetrievalBatchMapper ragRetrievalBatchMapper;

    @Autowired
    private RagHitLogMapper ragHitLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final Set<String> batchIds = new LinkedHashSet<>();
    private final Set<Long> documentIds = new LinkedHashSet<>();
    private final Set<Long> answerIds = new LinkedHashSet<>();
    private final Set<Long> interviewQuestionIds = new LinkedHashSet<>();
    private final Set<Long> sessionIds = new LinkedHashSet<>();
    private final Set<Long> questionIds = new LinkedHashSet<>();
    private final Set<Long> userIds = new LinkedHashSet<>();

    @BeforeEach
    void setUpTransactionTemplate() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUpExactFixtures() {
        transactionTemplate.executeWithoutResult(status -> {
            for (String batchId : batchIds) {
                jdbcTemplate.update(
                        "DELETE FROM rag_hit_log WHERE retrieval_batch_id = ?",
                        batchId
                );
            }
            for (String batchId : batchIds) {
                jdbcTemplate.update(
                        "DELETE FROM rag_retrieval_batch WHERE retrieval_batch_id = ?",
                        batchId
                );
            }
            for (Long documentId : documentIds) {
                jdbcTemplate.update(
                        "DELETE FROM knowledge_chunk WHERE document_id = ?",
                        documentId
                );
            }
            for (Long documentId : documentIds) {
                jdbcTemplate.update(
                        "DELETE FROM knowledge_document WHERE id = ?",
                        documentId
                );
            }
            for (Long answerId : answerIds) {
                jdbcTemplate.update(
                        "DELETE FROM interview_answer WHERE id = ?",
                        answerId
                );
            }
            for (Long interviewQuestionId : interviewQuestionIds) {
                jdbcTemplate.update(
                        "DELETE FROM interview_question WHERE id = ?",
                        interviewQuestionId
                );
            }
            for (Long sessionId : sessionIds) {
                jdbcTemplate.update(
                        "DELETE FROM interview_session WHERE id = ?",
                        sessionId
                );
            }
            for (Long questionId : questionIds) {
                jdbcTemplate.update(
                        "DELETE FROM question WHERE id = ?",
                        questionId
                );
            }
            for (Long userId : userIds) {
                jdbcTemplate.update(
                        "DELETE FROM `user` WHERE id = ?",
                        userId
                );
            }
        });

        for (String batchId : batchIds) {
            assertThat(countByBatchId("rag_hit_log", batchId)).isZero();
            assertThat(countByBatchId("rag_retrieval_batch", batchId)).isZero();
        }

        batchIds.clear();
        documentIds.clear();
        answerIds.clear();
        interviewQuestionIds.clear();
        sessionIds.clear();
        questionIds.clear();
        userIds.clear();
    }

    @Test
    void shouldCommitBatchAndThreeEvidenceHitsThroughSpringTransactionProxy()
            throws Exception {
        Fixture fixture = insertFixture(3);
        String batchId = trackBatchId();
        EvaluationRetrievalResult retrievalResult = retrievalResult(
                5,
                List.of(
                        evidence(1, fixture, 0, 0.91D),
                        evidence(3, fixture, 1, 0.81D),
                        evidence(5, fixture, 2, 0.71D)
                )
        );

        assertThat(AopUtils.isAopProxy(ragTracePersistenceService)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();

        ragTracePersistenceService.persist(batchId, fixture.answerId(), retrievalResult);

        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();
        assertThat(countByBatchId("rag_retrieval_batch", batchId)).isEqualTo(1);
        assertThat(countByBatchId("rag_hit_log", batchId)).isEqualTo(3);

        RagRetrievalBatch batch = ragRetrievalBatchMapper.getByBatchId(batchId);
        List<RagHitLog> hits = ragHitLogMapper.listByBatchId(batchId);
        assertThat(batch.getRawHitCount()).isEqualTo(5);
        assertThat(batch.getEvidenceHitCount()).isEqualTo(3);
        assertThat(hits).extracting(RagHitLog::getRankNo)
                .containsExactly(1, 3, 5);
        assertThat(hits).extracting(RagHitLog::getChunkId)
                .containsExactlyElementsOf(fixture.chunkIds());
        for (RagHitLog hit : hits) {
            assertThat(hit.getAnswerId()).isEqualTo(fixture.answerId());
            assertThat(hit.getQueryText()).isEqualTo(batch.getQueryText());
            assertThat(hit.getTopK()).isEqualTo(batch.getTopK());
            assertThat(hit.getEmbeddingModel()).isEqualTo(batch.getEmbeddingModel());
            assertThat(hit.getEmbeddingVersion()).isEqualTo(batch.getEmbeddingVersion());
            assertThat(objectMapper.readTree(hit.getFilterSnapshot()))
                    .isEqualTo(objectMapper.readTree(batch.getFilterSnapshot()));
        }
    }

    @Test
    void shouldCommitBatchWithoutHitsWhenRawHitsHaveNoEligibleEvidence() {
        Fixture fixture = insertFixture(0);
        String batchId = trackBatchId();

        ragTracePersistenceService.persist(
                batchId,
                fixture.answerId(),
                retrievalResult(5, List.of())
        );

        assertThat(countByBatchId("rag_retrieval_batch", batchId)).isEqualTo(1);
        assertThat(countByBatchId("rag_hit_log", batchId)).isZero();
        RagRetrievalBatch batch = ragRetrievalBatchMapper.getByBatchId(batchId);
        assertThat(batch.getRawHitCount()).isEqualTo(5);
        assertThat(batch.getEvidenceHitCount()).isZero();
    }

    @Test
    void shouldRollbackPreviouslyInsertedBatchWhenHitForeignKeyFails() {
        Fixture fixture = insertFixture(0);
        String batchId = trackBatchId();
        RetrievedChunk missingChunkEvidence = new RetrievedChunk(
                1,
                0.9D,
                Long.MAX_VALUE,
                999L,
                1,
                1,
                "missing-vector",
                "missing chunk",
                KnowledgeCategory.JAVA_COLLECTION,
                "test source",
                "missing content"
        );

        assertThatThrownBy(() -> ragTracePersistenceService.persist(
                batchId,
                fixture.answerId(),
                retrievalResult(1, List.of(missingChunkEvidence))
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countByBatchId("rag_retrieval_batch", batchId)).isZero();
        assertThat(countByBatchId("rag_hit_log", batchId)).isZero();
    }

    private Fixture insertFixture(int chunkCount) {
        Fixture fixture = transactionTemplate.execute(status -> {
            String unique = uniqueValue();
            String account = "f4b-" + unique;
            jdbcTemplate.update(
                    """
                    INSERT INTO `user`
                        (account, username, password, email, role, status)
                    VALUES (?, 'F4-B测试用户', 'test-password-hash', ?, 'USER', 'ENABLED')
                    """,
                    account,
                    account + "@example.com"
            );
            long userId = requiredId("SELECT id FROM `user` WHERE account = ?", account);
            userIds.add(userId);

            String knowledgePoint = "F4-B-RAG-" + unique;
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
            questionIds.add(questionId);

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
            sessionIds.add(sessionId);

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
            interviewQuestionIds.add(interviewQuestionId);

            String requestId = "f4b-answer-" + unique;
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
            long answerId = requiredId(
                    "SELECT id FROM interview_answer WHERE request_id = ?",
                    requestId
            );
            answerIds.add(answerId);

            List<Long> chunkIds = new ArrayList<>();
            if (chunkCount > 0) {
                String title = "F4-B RAG trace " + unique;
                jdbcTemplate.update(
                        """
                        INSERT INTO knowledge_document
                            (title, category, file_name, file_type, content, source,
                             content_hash, document_version, processing_status,
                             error_message)
                        VALUES (?, 'JAVA_COLLECTION', ?, 'MARKDOWN', ?, ?, ?, 1,
                                'READY', NULL)
                        """,
                        title,
                        "f4b-" + unique + ".md",
                        "# F4-B fixture\n" + unique,
                        "F4-B Transaction Test",
                        uniqueHash()
                );
                long documentId = requiredId(
                        "SELECT id FROM knowledge_document WHERE title = ?",
                        title
                );
                documentIds.add(documentId);

                for (int index = 1; index <= chunkCount; index++) {
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
                            "F4-B knowledge chunk " + index,
                            vectorId,
                            EMBEDDING_MODEL,
                            EMBEDDING_VERSION
                    );
                    chunkIds.add(requiredId(
                            "SELECT id FROM knowledge_chunk WHERE vector_id = ?",
                            vectorId
                    ));
                }
                return new Fixture(answerId, documentId, List.copyOf(chunkIds));
            }

            return new Fixture(answerId, null, List.of());
        });

        assertThat(fixture).isNotNull();
        return fixture;
    }

    private EvaluationRetrievalResult retrievalResult(
            int rawHitCount,
            List<RetrievedChunk> evidence
    ) {
        List<VectorSearchHit> rawHits = java.util.stream.IntStream
                .range(0, rawHitCount)
                .mapToObj(index -> new VectorSearchHit(
                        "raw-vector-" + UUID.randomUUID(),
                        500L + index,
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
            Fixture fixture,
            int chunkOffset,
            double score
    ) {
        return new RetrievedChunk(
                rank,
                score,
                fixture.chunkIds().get(chunkOffset),
                fixture.documentId(),
                1,
                chunkOffset + 1,
                "eligible-vector-" + rank,
                "HashMap 文档",
                KnowledgeCategory.JAVA_COLLECTION,
                "F4-B Transaction Test",
                "HashMap evidence " + rank
        );
    }

    private String trackBatchId() {
        String batchId = UUID.randomUUID().toString();
        batchIds.add(batchId);
        return batchId;
    }

    private int countByBatchId(String table, String batchId) {
        String sql = switch (table) {
            case "rag_retrieval_batch" ->
                    "SELECT COUNT(*) FROM rag_retrieval_batch WHERE retrieval_batch_id = ?";
            case "rag_hit_log" ->
                    "SELECT COUNT(*) FROM rag_hit_log WHERE retrieval_batch_id = ?";
            default -> throw new IllegalArgumentException("不支持的表名");
        };
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, batchId);
        return count == null ? 0 : count;
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

    private record Fixture(
            long answerId,
            Long documentId,
            List<Long> chunkIds
    ) {
    }
}
