package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewWorkflowTransactionServiceIntegrationTest {

    @Autowired
    private InterviewWorkflowTransactionService transactionService;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long sessionId;
    private Long currentQuestionId;
    private Long nextQuestionId;
    private Long currentQuestionBankId;
    private Long nextQuestionBankId;
    private Long answerId;
    private Long documentId;
    private Long chunkId;

    @Autowired
    private com.kun.aiinterview.knowledge.service.RagTracePersistenceService traceService;

    @AfterEach
    void cleanUp() {
        if (answerId != null) {
            jdbcTemplate.update("DELETE FROM rag_hit_log WHERE answer_id = ?", answerId);
            jdbcTemplate.update("DELETE FROM rag_retrieval_batch WHERE answer_id = ?", answerId);
            jdbcTemplate.update("DELETE FROM answer_evaluation WHERE answer_id = ?", answerId);
            jdbcTemplate.update(
                    "DELETE FROM interview_answer WHERE id = ?",
                    answerId
            );
        }

        if (documentId != null) {
            jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE document_id = ?", documentId);
            jdbcTemplate.update("DELETE FROM knowledge_document WHERE id = ?", documentId);
        }
        if (currentQuestionId != null || nextQuestionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_question WHERE session_id = ?",
                    sessionId
            );
        }

        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }

        if (currentQuestionBankId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    currentQuestionBankId
            );
        }

        if (nextQuestionBankId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    nextQuestionBankId
            );
        }

        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    userId
            );
        }
    }

    private AnswerEvaluation evaluation() {
        return AnswerEvaluation.builder()
                .answerId(answerId).mainInterviewQuestionId(currentQuestionId)
                .evaluationPhase(EvaluationPhase.FINAL)
                .decisionAction(DecisionAction.NEXT_MAIN)
                .correctnessScore(16).completenessScore(16).depthScore(16).clarityScore(16).practiceScore(16)
                .totalScore(80).level("GOOD").strengths("[]").missingPoints("[]").correction("")
                .scoringPointResults("[]").followUpRecommended(false).retrievalBatchId("test-batch")
                .llmModel("fake").promptVersion("fake").evaluationStandardVersion("fake").rawResult("{}")
                .build();
    }

    @Test
    void shouldRollbackEarlierUpdatesWhenSessionCasFails() {
        prepareWorkflowState();
        jdbcTemplate.update("UPDATE interview_answer SET attempt_id = ? WHERE id = ?", "attempt-test", answerId);

        InterviewQuestion currentQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        currentQuestionId
                );
        InterviewQuestion nextQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        nextQuestionId
                );
        InterviewSession staleSession =
                interviewSessionMapper.getInterviewSessionById(sessionId);
        staleSession.setVersion(staleSession.getVersion() + 1);

        assertThatThrownBy(
                () -> transactionService.advanceToNextMain(
                        answerId,
                        currentQuestion,
                        staleSession,
                        nextQuestion,
                        "attempt-test",
                        evaluation(), trace())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session推进到下一道MAIN失败");

        assertThat(answerStatus())
                .isEqualTo(InterviewAnswerStatus.EVALUATING.name());
        assertThat(questionStatus(nextQuestionId))
                .isEqualTo(InterviewQuestionStatus.PENDING.name());

        InterviewSession unchangedSession =
                interviewSessionMapper.getInterviewSessionById(sessionId);
        assertThat(unchangedSession.getCurrentInterviewQuestionId())
                .isEqualTo(currentQuestionId);
        assertThat(unchangedSession.getCompletedQuestionCount())
                .isZero();
        assertThat(unchangedSession.getVersion()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?", Long.class, answerId)).isZero();
        assertTraceCounts(0);
        transactionService.markEvaluationFailed(answerId, "TEST", "attempt-test");
        assertThat(transactionService.tryRetryEvaluation(answerId, "retry-attempt")).isTrue();
        transactionService.advanceToNextMain(answerId, currentQuestion, unchangedSession, nextQuestion,
                "retry-attempt", evaluation(), trace());
        assertThat(answerStatus()).isEqualTo("EVALUATED");
        assertTraceCounts(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?", Long.class, answerId)).isEqualTo(1);

    }

    private void prepareWorkflowState() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");
        String account = "workflow-tx-" + unique;

        jdbcTemplate.update(
                """
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Workflow事务测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        userId = requiredId(
                "SELECT id FROM `user` WHERE account = ?",
                account
        );

        currentQuestionBankId = insertQuestion(
                "Current-" + unique
        );
        nextQuestionBankId = insertQuestion(
                "Next-" + unique
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', 2, 0, 'NOT_STARTED', 0)
                """,
                userId
        );
        sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );

        currentQuestionId = insertInterviewQuestion(
                currentQuestionBankId,
                1,
                1,
                InterviewQuestionStatus.ANSWERED.name()
        );
        nextQuestionId = insertInterviewQuestion(
                nextQuestionBankId,
                2,
                3,
                InterviewQuestionStatus.PENDING.name()
        );

        jdbcTemplate.update(
                """
                UPDATE interview_session
                SET current_interview_question_id = ?
                WHERE id = ?
                """,
                currentQuestionId,
                sessionId
        );

        String requestId = "workflow-tx-request-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, 'EVALUATING', ?)
                """,
                currentQuestionId,
                "HashMap 使用数组、链表和红黑树存储数据。",
                requestId
        );
        answerId = requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    @Test
    void staleAttemptCannotCommitTraceOrEvaluationAndFailureAuditIsFenced() {
        prepareWorkflowState();
        jdbcTemplate.update("UPDATE interview_answer SET attempt_id = 'attempt-A' WHERE id = ?", answerId);
        var current = interviewQuestionMapper.getInterviewQuestionById(currentQuestionId);
        var session = interviewSessionMapper.getInterviewSessionById(sessionId);
        var next = interviewQuestionMapper.getInterviewQuestionById(nextQuestionId);
        var prepared = evaluation();
        var retrieval = trace();
        jdbcTemplate.update("UPDATE interview_answer SET updated_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusMinutes(20), answerId);
        assertThat(transactionService.tryReclaimStaleEvaluating(answerId,
                java.time.LocalDateTime.now().minusMinutes(10), "attempt-B")).isTrue();
        assertThatThrownBy(() -> transactionService.advanceToNextMain(
                answerId, current, session, next, "attempt-A", prepared, retrieval))
                .isInstanceOf(com.kun.aiinterview.common.exception.ConflictException.class);
        traceService.persistFailedAttempt("failed-old", answerId, retrieval, "attempt-A");
        assertTraceCounts(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?", Long.class, answerId)).isZero();
        transactionService.advanceToNextMain(answerId, current, session, next, "attempt-B", evaluation(), retrieval);
        assertTraceCounts(1);
    }

    @Test
    void legacyReplayDoesNotDuplicateTraceAndChunkForeignKeyRestrictsDeletion() {
        prepareWorkflowState();
        jdbcTemplate.update("UPDATE interview_answer SET attempt_id = 'attempt-A' WHERE id = ?", answerId);
        var saved = evaluation();
        traceService.persist(saved.getRetrievalBatchId(), answerId, trace());
        jdbcTemplate.update("""
                INSERT INTO answer_evaluation
                  (answer_id, main_interview_question_id, evaluation_phase, correctness_score,
                   completeness_score, depth_score, clarity_score, practice_score, total_score,
                   level, strengths, missing_points, correction, scoring_point_results,
                   follow_up_recommended, decision_action, retrieval_batch_id,
                   llm_model, prompt_version, evaluation_standard_version, raw_result)
                VALUES (?, ?, 'FINAL',16,16,16,16,16,80,'GOOD','[]','[]','','[]',false,'NEXT_MAIN',
                        'test-batch','fake','fake','fake','{}')
                """, answerId, currentQuestionId);
        saved.setId(requiredId("SELECT id FROM answer_evaluation WHERE answer_id = ?", answerId));
        transactionService.advanceToNextMain(answerId,
                interviewQuestionMapper.getInterviewQuestionById(currentQuestionId),
                interviewSessionMapper.getInterviewSessionById(sessionId),
                interviewQuestionMapper.getInterviewQuestionById(nextQuestionId),
                "attempt-A", saved, null);
        assertTraceCounts(1);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE id = ?", chunkId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void ownedFailedPreparationKeepsDiagnosticBatchWithoutAnEvaluation() {
        prepareWorkflowState();
        jdbcTemplate.update("UPDATE interview_answer SET attempt_id = 'attempt-A' WHERE id = ?", answerId);
        traceService.persistFailedAttempt("diagnostic", answerId, trace(), "attempt-A");
        assertTraceCounts(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?", Long.class, answerId)).isZero();
    }

    @Test
    void maintenanceSqlPreservesExistingInactiveChunkAndReportsOnlyOrphan(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        prepareWorkflowState();
        trace();
        jdbcTemplate.update("UPDATE knowledge_chunk SET status = 'INACTIVE' WHERE id = ?", chunkId);
        String owned = jdbcTemplate.queryForObject(
                "SELECT vector_id FROM knowledge_chunk WHERE id = ?", String.class, chunkId);
        var client = org.mockito.Mockito.mock(io.milvus.v2.client.MilvusClientV2.class);
        var iterator = org.mockito.Mockito.mock(io.milvus.orm.iterator.QueryIterator.class);
        var existing = new io.milvus.response.QueryResultsWrapper.RowRecord();
        existing.put("vector_id", owned.toUpperCase(java.util.Locale.ROOT));
        var orphan = new io.milvus.response.QueryResultsWrapper.RowRecord();
        orphan.put("vector_id", "orphan-" + UUID.randomUUID());
        org.mockito.Mockito.when(client.queryIterator(org.mockito.ArgumentMatchers.any())).thenReturn(iterator);
        org.mockito.Mockito.when(iterator.next()).thenReturn(java.util.List.of(existing, orphan), java.util.List.of());
        var properties = new com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties();
        properties.setDatabaseName("default");
        properties.setCollectionName("isolated-test");
        var tool = new com.kun.aiinterview.knowledge.maintenance.OrphanVectorMaintenance(
                jdbcTemplate, client, properties, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        var summary = tool.run(new com.kun.aiinterview.knowledge.maintenance.OrphanVectorMaintenance.Options(
                false, true, true, "ai_interview_test/default/isolated-test", 2, temp.resolve("scan.tsv")));
        assertThat(summary.scanned()).isEqualTo(2);
        assertThat(summary.candidates()).isEqualTo(1);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingEvidenceChunkRollsBackBatchEvaluationAndWorkflow() {
        prepareWorkflowState();
        jdbcTemplate.update("UPDATE interview_answer SET attempt_id = 'attempt-A' WHERE id = ?", answerId);
        var retrieval = trace();
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE id = ?", chunkId);
        assertThatThrownBy(() -> transactionService.advanceToNextMain(answerId,
                interviewQuestionMapper.getInterviewQuestionById(currentQuestionId),
                interviewSessionMapper.getInterviewSessionById(sessionId),
                interviewQuestionMapper.getInterviewQuestionById(nextQuestionId),
                "attempt-A", evaluation(), retrieval))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertTraceCounts(0);
        assertThat(answerStatus()).isEqualTo("EVALUATING");
        assertThat(interviewSessionMapper.getInterviewSessionById(sessionId).getVersion()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?", Long.class, answerId)).isZero();
    }

    private void assertTraceCounts(int expected) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_retrieval_batch WHERE answer_id = ?", Long.class, answerId))
                .isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_hit_log WHERE answer_id = ?", Long.class, answerId))
                .isEqualTo(expected);
    }

    private long insertQuestion(String knowledgePoint) {
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

        return requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );
    }

    private long insertInterviewQuestion(
            long questionId,
            int planOrder,
            int displayOrder,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, plan_order,
                     display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        ?, ?, ?)
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                "[{\"scoringPointId\":101,\"pointType\":\"CORE\","
                        + "\"content\":\"说明核心机制\",\"weight\":100}]",
                planOrder,
                displayOrder,
                status
        );

        return requiredId(
                """
                SELECT id
                FROM interview_question
                WHERE session_id = ? AND plan_order = ?
                """,
                sessionId,
                planOrder
        );
    }

    private String answerStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM interview_answer WHERE id = ?",
                String.class,
                answerId
        );
    }

    private String questionStatus(long interviewQuestionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM interview_question WHERE id = ?",
                String.class,
                interviewQuestionId
        );
    }

    private long requiredId(String sql, Object... arguments) {
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                arguments
        );
        assertThat(id).isNotNull();
        return id;
    }
    private com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult trace() {
        if (documentId == null) {
            String title = "trace-atomic-" + UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO knowledge_document
                    (title, file_name, file_type, category, content, content_hash,
                     document_version, processing_status)
                    VALUES (?, 'test.md', 'MARKDOWN', 'JAVA_COLLECTION', 'evidence', ?, 1, 'READY')
                    """, title, UUID.randomUUID().toString().replace("-", "").repeat(2));
            documentId = requiredId("SELECT id FROM knowledge_document WHERE title = ?", title);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_chunk
                    (document_id, document_version, chunk_index, content,
                     vector_id, embedding_model, embedding_version, status)
                    VALUES (?, 1, 1, 'evidence', ?, 'test-model', 'test-v1', 'ACTIVE')
                    """, documentId, title);
            chunkId = requiredId("SELECT id FROM knowledge_chunk WHERE document_id = ?", documentId);
        }
        var evidence = new com.kun.aiinterview.knowledge.retrieval.RetrievedChunk(
                1, 0.8, chunkId, documentId, 1, 1, "test-vector", "test-title",
                com.kun.aiinterview.knowledge.enums.KnowledgeCategory.JAVA_COLLECTION, null, "evidence");
        return new com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult(
                "test query", 5, "test-model", "test-v1", java.util.List.of(
                        new com.kun.aiinterview.knowledge.vector.VectorSearchHit(
                                "test-vector", documentId, 1, "test-v1", 0.8)),
                java.util.List.of(evidence));
    }

}
