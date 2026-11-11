package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// No test transaction: claim, FAILED write and completion must commit independently.
@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewPostClaimFailureIntegrationTest {

    @Autowired private InterviewAnswerMapper answers;
    @Autowired private InterviewQuestionMapper questions;
    @Autowired private InterviewSessionMapper sessions;
    @Autowired private AnswerEvaluationMapper evaluations;
    @Autowired private InterviewWorkflowTransactionService transactions;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private InterviewQuestionMapper queryBoundary;
    private EvaluationOrchestrationService orchestration;
    private InterviewWorkflowService workflow;
    private InterviewService service;
    private Long userId;
    private Long bankQuestionId;
    private Long sessionId;
    private Long mainId;
    private Long currentId;
    private Long answerId;
    private SubmitInterviewAnswerRequest request;

    @BeforeEach
    void setUp() {
        queryBoundary = mock(InterviewQuestionMapper.class, delegatesTo(questions));
        orchestration = mock(EvaluationOrchestrationService.class);
        workflow = new InterviewWorkflowService(answers, queryBoundary, sessions, transactions,
                orchestration, objectMapper, new StaleRecoveryProperties());
        @SuppressWarnings("unchecked")
        ObjectProvider<InterviewWorkflowService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(workflow);
        service = new InterviewService(mock(InterviewSessionService.class), sessions, questions,
                answers, transactions, evaluations, provider, objectMapper);
    }

    @ParameterizedTest
    @EnumSource(InterviewQuestionType.class)
    void queryFailureMustCommitFailedAndAllowExactRequestRetry(InterviewQuestionType type) throws Exception {
        seedSubmittedAnswer(type);
        assertThat(readCommittedAnswer()).isEqualTo(new AnswerState("SUBMITTED", null, null));
        var sessionBefore = jdbc.queryForMap("SELECT * FROM interview_session WHERE id = ?", sessionId);
        var questionsBefore = jdbc.queryForList(
                "SELECT * FROM interview_question WHERE session_id = ? ORDER BY id", sessionId);
        AtomicReference<String> attempt = new AtomicReference<>();
        SQLException cause = new SQLException("injected next MAIN query failure");
        var failure = new DataAccessResourceFailureException("next MAIN query unavailable", cause);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            AnswerState claimed = readCommittedAnswer();
            assertThat(claimed.status()).isEqualTo("EVALUATING");
            assertThat(claimed.attemptId()).matches("[0-9a-f]{32}");
            assertThat(claimed.errorCode()).isNull();
            attempt.set(claimed.attemptId());
            throw failure;
        }).when(queryBoundary).findNextPendingMainQuestion(sessionId, 1);

        assertThatThrownBy(() -> service.submitAnswer(userId, sessionId, request))
                .isSameAs(failure).hasCause(cause);
        AnswerState failed = readCommittedAnswer();
        assertThat(failed).as("post-claim query failure must be durably FAILED immediately")
                .isEqualTo(new AnswerState("FAILED", attempt.get(), "EVALUATION_ERROR"));
        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT * FROM interview_session WHERE id = ?", sessionId))
                .isEqualTo(sessionBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_question WHERE session_id = ? ORDER BY id", sessionId))
                .isEqualTo(questionsBefore);
        assertCounts(0);
        verifyNoInteractions(orchestration);

        // Restore the real SELECT, then use the same requestId, question and exact answer content.
        doAnswer(invocation -> questions.findNextPendingMainQuestion(sessionId, 1))
                .when(queryBoundary).findNextPendingMainQuestion(sessionId, 1);
        when(orchestration.evaluate(eq(answerId), eq(false), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            AnswerState retried = readCommittedAnswer();
            assertThat(retried.status()).isEqualTo("EVALUATING");
            assertThat(retried.attemptId()).isEqualTo(invocation.getArgument(2)).isNotEqualTo(attempt.get());
            assertThat(retried.errorCode()).isNull();
            return finishResult();
        });
        var response = service.submitAnswer(userId, sessionId, request);
        assertThat(response.nextAction()).isEqualTo(DecisionAction.FINISH);
        assertThat(response.sessionStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(readCommittedAnswer().status()).isEqualTo("EVALUATED");
        assertThat(readCommittedAnswer().attemptId()).isNotEqualTo(attempt.get());
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isEqualTo(1);
        assertThat(sessions.getInterviewSessionById(sessionId).getCompletedQuestionCount()).isEqualTo(1);
        assertCounts(1);
        assertThat(service.submitAnswer(userId, sessionId, request)).isEqualTo(response);
        assertCounts(1);
        verify(orchestration, times(1)).evaluate(eq(answerId), eq(false), anyString());
    }

    @Test
    void lateQueryFailureMustNotMarkReplacementAttemptFailed() throws Exception {
        seedSubmittedAnswer(InterviewQuestionType.MAIN);
        var failure = new DataAccessResourceFailureException("old query failed after replacement claimed");
        AtomicReference<String> oldAttempt = new AtomicReference<>();
        String replacementAttempt = UUID.randomUUID().toString().replace("-", "");
        doAnswer(invocation -> {
            AnswerState claimed = readCommittedAnswer();
            assertThat(claimed.status()).isEqualTo("EVALUATING");
            oldAttempt.set(claimed.attemptId());
            // Deterministic takeover during the query boundary; only this fixture is made stale.
            jdbc.update("UPDATE interview_answer SET updated_at = ? WHERE id = ?",
                    LocalDateTime.now().minusMinutes(20), answerId);
            assertThat(transactions.tryReclaimStaleEvaluating(answerId,
                    LocalDateTime.now().minusMinutes(10), replacementAttempt)).isTrue();
            throw failure;
        }).when(queryBoundary).findNextPendingMainQuestion(sessionId, 1);

        assertThatThrownBy(() -> workflow.evaluateAnswer(answerId)).isSameAs(failure);
        assertThat(failure.getSuppressed()).singleElement().satisfies(error ->
                assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage("Answer失败状态更新失败"));
        assertThat(oldAttempt.get()).isNotBlank().isNotEqualTo(replacementAttempt);
        assertThat(readCommittedAnswer()).isEqualTo(new AnswerState("EVALUATING", replacementAttempt, null));
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isZero();
        assertCounts(0);
        verifyNoInteractions(orchestration);
    }

    private AnswerState readCommittedAnswer() throws SQLException {
        // Obtain an independent connection, not a transaction-bound MyBatis/JDBC session.
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT status, attempt_id, error_code FROM interview_answer WHERE id = ?")) {
            statement.setLong(1, answerId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return new AnswerState(rows.getString(1), rows.getString(2), rows.getString(3));
            }
        }
    }

    private void assertCounts(int expectedEvaluations) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer WHERE interview_question_id = ?",
                Integer.class, currentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?",
                Integer.class, answerId)).isEqualTo(expectedEvaluations);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_retrieval_batch WHERE answer_id = ?",
                Integer.class, answerId)).isEqualTo(expectedEvaluations);
    }

    private EvaluationOrchestrationResult finishResult() {
        String batch = "b2-" + UUID.randomUUID();
        AnswerEvaluation evaluation = AnswerEvaluation.builder()
                .answerId(answerId).mainInterviewQuestionId(mainId)
                .evaluationPhase(EvaluationPhase.FINAL).decisionAction(DecisionAction.FINISH)
                .correctnessScore(16).completenessScore(16).depthScore(16).clarityScore(16).practiceScore(16)
                .totalScore(80).level("GOOD").strengths("[]").missingPoints("[]").correction("")
                .scoringPointResults("[]").followUpRecommended(false).retrievalBatchId(batch)
                .llmModel("test").promptVersion("test").evaluationStandardVersion("test").rawResult("{}")
                .build();
        return new EvaluationOrchestrationResult(null, answerId, mainId, EvaluationPhase.FINAL,
                DecisionAction.FINISH, 80, EvaluationLevel.GOOD, false, null, List.of(), batch, evaluation,
                new EvaluationRetrievalResult("test query", 5, "test", "test-v1", List.of(), List.of()));
    }

    private void seedSubmittedAnswer(InterviewQuestionType type) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        String unique = UUID.randomUUID().toString().replace("-", "");
        String account = "b2-" + unique;
        jdbc.update("""
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, 'B2 test', 'test-hash', ?, 'USER', 'ENABLED')
                """, account, account + "@example.com");
        userId = jdbc.queryForObject("SELECT id FROM `user` WHERE account = ?", Long.class, account);
        jdbc.update("""
                INSERT INTO question (category, knowledge_point, difficulty, question_content, reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', 'HashMap?', 'test reference', 'ENABLED')
                """, account);
        bankQuestionId = jdbc.queryForObject("SELECT id FROM question WHERE knowledge_point = ?", Long.class, account);
        jdbc.update("""
                INSERT INTO interview_session (user_id, difficulty, status, planned_question_count,
                    completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', 1, 0, 'NOT_STARTED', 0)
                """, userId);
        sessionId = jdbc.queryForObject("SELECT id FROM interview_session WHERE user_id = ?", Long.class, userId);
        jdbc.update("""
                INSERT INTO interview_question (session_id, question_id, category, knowledge_point,
                    question_content, reference_answer_snapshot, scoring_points_snapshot,
                    question_type, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', 'HashMap?', 'test reference',
                    '[{"id":101,"pointType":"CORE","content":"test point","weight":100}]',
                    'MAIN', 1, 1, ?)
                """, sessionId, bankQuestionId, type == InterviewQuestionType.MAIN ? "WAITING_ANSWER" : "ANSWERED");
        mainId = jdbc.queryForObject("SELECT id FROM interview_question WHERE session_id = ?", Long.class, sessionId);
        currentId = mainId;
        if (type == InterviewQuestionType.FOLLOW_UP) {
            jdbc.update("""
                    INSERT INTO interview_question (session_id, category, knowledge_point, question_content,
                        question_type, parent_question_id, display_order, follow_up_target_points, status)
                    VALUES (?, 'JAVA_COLLECTION', 'HashMap', 'Further details?', 'FOLLOW_UP', ?, 2, '[101]', 'WAITING_ANSWER')
                    """, sessionId, mainId);
            currentId = jdbc.queryForObject("SELECT id FROM interview_question WHERE parent_question_id = ?", Long.class, mainId);
        }
        jdbc.update("UPDATE interview_session SET current_interview_question_id = ? WHERE id = ?", currentId, sessionId);
        request = new SubmitInterviewAnswerRequest(currentId, "HashMap uses buckets.", "b2-" + unique);
        InterviewAnswer submitted = transactions.submitAnswer(InterviewAnswer.builder()
                .answerContent(request.answerContent()).requestId(request.requestId()).build(),
                questions.getInterviewQuestionById(currentId));
        answerId = submitted.getId();
    }

    @AfterEach
    void cleanUp() {
        if (answerId != null) {
            jdbc.update("DELETE FROM rag_hit_log WHERE answer_id = ?", answerId);
            jdbc.update("DELETE FROM answer_evaluation WHERE answer_id = ?", answerId);
            jdbc.update("DELETE FROM rag_retrieval_batch WHERE answer_id = ?", answerId);
            jdbc.update("DELETE FROM interview_answer WHERE id = ?", answerId);
        }
        if (sessionId != null) {
            jdbc.update("DELETE FROM interview_question WHERE session_id = ? AND question_type = 'FOLLOW_UP'", sessionId);
            jdbc.update("DELETE FROM interview_question WHERE session_id = ?", sessionId);
            jdbc.update("DELETE FROM interview_session WHERE id = ?", sessionId);
        }
        if (bankQuestionId != null) jdbc.update("DELETE FROM question WHERE id = ?", bankQuestionId);
        if (userId != null) jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
    }

    private record AnswerState(String status, String attemptId, String errorCode) { }
}
