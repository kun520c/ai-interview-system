package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Import(InterviewEvaluationClaimConcurrencyIntegrationTest.PausingMapperConfiguration.class)
class InterviewEvaluationClaimConcurrencyIntegrationTest {

    @Autowired
    private InterviewAnswerMapper interviewAnswerMapper;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private InterviewWorkflowTransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EvaluationClaimBarrier claimBarrier;

    private EvaluationOrchestrationService evaluationOrchestrationService;
    private InterviewWorkflowService workflowService;

    private Long userId;
    private Long questionId;
    private Long sessionId;
    private Long interviewQuestionId;
    private Long answerId;

    @BeforeEach
    void setUp() {
        evaluationOrchestrationService = mock(EvaluationOrchestrationService.class);
        workflowService = new InterviewWorkflowService(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper,
                transactionService,
                evaluationOrchestrationService,
                objectMapper,
                new StaleRecoveryProperties()
        );
    }

    @AfterEach
    void cleanUp() {
        claimBarrier.disarm();
        if (answerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_answer WHERE id = ?",
                    answerId
            );
        }
        if (interviewQuestionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_question WHERE id = ?",
                    interviewQuestionId
            );
        }
        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }
        if (questionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    questionId
            );
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
    }

    @Test
    void concurrentSubmittedClaimShouldHaveOneEvaluatorAndNeverReturn500()
            throws Exception {
        seedAnswer(InterviewAnswerStatus.SUBMITTED);
        runConcurrentEvaluationClaim(
                EvaluationClaimBarrier.Mode.CLAIM
        );
    }

    @Test
    void concurrentFailedRetryShouldHaveOneEvaluatorAndNeverReturn500()
            throws Exception {
        seedAnswer(InterviewAnswerStatus.FAILED);
        runConcurrentEvaluationClaim(
                EvaluationClaimBarrier.Mode.RETRY
        );
    }

    private void runConcurrentEvaluationClaim(
            EvaluationClaimBarrier.Mode mode
    ) throws Exception {
        CountDownLatch enteredCas = new CountDownLatch(2);
        CountDownLatch startCas = new CountDownLatch(1);
        claimBarrier.arm(answerId, mode, enteredCas, startCas);

        CountDownLatch evaluateEntered = new CountDownLatch(1);
        CountDownLatch releaseEvaluate = new CountDownLatch(1);
        AtomicInteger evaluateCalls = new AtomicInteger();
        when(evaluationOrchestrationService.evaluate(eq(answerId), anyBoolean()))
                .thenAnswer(invocation -> {
                    evaluateCalls.incrementAndGet();
                    evaluateEntered.countDown();
                    if (!releaseEvaluate.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "evaluation release latch timed out"
                        );
                    }
                    return finishResult();
                });

        var executor = Executors.newFixedThreadPool(2);
        var completion = new ExecutorCompletionService<ClaimOutcome>(executor);
        try {
            completion.submit(() -> runEvaluateAnswer());
            completion.submit(() -> runEvaluateAnswer());

            assertThat(enteredCas.await(10, TimeUnit.SECONDS))
                    .as("both threads should reach the evaluation CAS")
                    .isTrue();
            startCas.countDown();

            assertThat(evaluateEntered.await(10, TimeUnit.SECONDS))
                    .as("exactly one CAS winner should enter evaluation")
                    .isTrue();

            Future<ClaimOutcome> firstCompleted =
                    completion.poll(10, TimeUnit.SECONDS);
            assertThat(firstCompleted).isNotNull();
            ClaimOutcome loser = firstCompleted.get(1, TimeUnit.SECONDS);
            assertThat(loser.error())
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("答案正在评估中，请稍后重试");
            assertThat(loser.error())
                    .isNotInstanceOf(IllegalStateException.class);
            assertThat(evaluateCalls.get()).isEqualTo(1);

            releaseEvaluate.countDown();
            Future<ClaimOutcome> secondCompleted =
                    completion.poll(10, TimeUnit.SECONDS);
            assertThat(secondCompleted).isNotNull();
            ClaimOutcome winner = secondCompleted.get(1, TimeUnit.SECONDS);
            assertThat(winner.error()).isNull();
            assertThat(winner.result()).isNotNull();
            assertThat(winner.result().decisionAction())
                    .isEqualTo(DecisionAction.FINISH);

            verify(evaluationOrchestrationService, times(1))
                    .evaluate(eq(answerId), anyBoolean());
            assertThat(evaluateCalls.get()).isEqualTo(1);
            assertThat(interviewAnswerMapper.getInterviewAnswerById(answerId)
                    .getStatus())
                    .isEqualTo(InterviewAnswerStatus.EVALUATED);
            assertThat(countAnswerEvaluations()).isZero();
        } finally {
            releaseEvaluate.countDown();
            executor.shutdownNow();
        }
    }

    private ClaimOutcome runEvaluateAnswer() {
        try {
            return new ClaimOutcome(
                    workflowService.evaluateAnswer(answerId),
                    null
            );
        } catch (RuntimeException exception) {
            return new ClaimOutcome(null, exception);
        }
    }

    private EvaluationOrchestrationResult finishResult() {
        return new EvaluationOrchestrationResult(
                401L,
                answerId,
                interviewQuestionId,
                EvaluationPhase.FINAL,
                DecisionAction.FINISH,
                80,
                EvaluationLevel.GOOD,
                false,
                null,
                List.of(),
                "retrieval-batch-claim"
        );
    }

    private long countAnswerEvaluations() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?",
                Long.class,
                answerId
        );
        assertThat(count).isNotNull();
        return count;
    }

    private void seedAnswer(InterviewAnswerStatus status) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String account = "claim-cas-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "评价抢占并发用户",
                "test-password-hash",
                account + "@example.com"
        );
        userId = requiredId("SELECT id FROM `user` WHERE account = ?", account);

        String knowledgePoint = "ClaimCas-" + unique;
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
        questionId = requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', 1, 0, 'NOT_STARTED', 0)
                """,
                userId
        );
        sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, plan_order,
                     display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        1, 1, 'ANSWERED')
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                "[{\"id\":101,\"pointType\":\"CORE\",\"content\":\"核心机制\",\"weight\":100}]"
        );
        interviewQuestionId = requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND plan_order = 1
                """,
                sessionId
        );
        jdbcTemplate.update(
                """
                UPDATE interview_session
                SET current_interview_question_id = ?
                WHERE id = ?
                """,
                interviewQuestionId,
                sessionId
        );

        String requestId = "claim-cas-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, ?, ?)
                """,
                interviewQuestionId,
                "HashMap 使用数组、链表和红黑树存储数据。",
                status.name(),
                requestId
        );
        answerId = requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    private long requiredId(String sql, Object... arguments) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        assertThat(id).isNotNull();
        return id;
    }

    private record ClaimOutcome(
            EvaluationOrchestrationResult result,
            RuntimeException error
    ) {
    }

    @TestConfiguration
    static class PausingMapperConfiguration {

        @Bean
        EvaluationClaimBarrier evaluationClaimBarrier() {
            return new EvaluationClaimBarrier();
        }

        @Bean
        @Primary
        InterviewAnswerMapper pausingInterviewAnswerMapper(
                @Qualifier("interviewAnswerMapper") InterviewAnswerMapper delegate,
                EvaluationClaimBarrier barrier
        ) {
            return new InterviewAnswerMapper() {
                @Override
                public InterviewAnswer getInterviewAnswerById(Long id) {
                    return delegate.getInterviewAnswerById(id);
                }

                @Override
                public InterviewAnswer getInterviewAnswerByInterviewQuestionId(
                        Long interviewQuestionId
                ) {
                    return delegate.getInterviewAnswerByInterviewQuestionId(
                            interviewQuestionId
                    );
                }

                @Override
                public InterviewAnswer getInterviewAnswerByRequestId(
                        String requestId
                ) {
                    return delegate.getInterviewAnswerByRequestId(requestId);
                }

                @Override
                public List<InterviewAnswer> listByInterviewQuestionIds(
                        List<Long> interviewQuestionIds
                ) {
                    return delegate.listByInterviewQuestionIds(
                            interviewQuestionIds
                    );
                }

                @Override
                public int insertInterviewAnswer(InterviewAnswer interviewAnswer) {
                    return delegate.insertInterviewAnswer(interviewAnswer);
                }

                @Override
                public int claimEvaluation(Long id) {
                    return barrier.awaitThenRun(
                            id,
                            EvaluationClaimBarrier.Mode.CLAIM,
                            () -> delegate.claimEvaluation(id)
                    );
                }

                @Override
                public int retryEvaluation(Long id) {
                    return barrier.awaitThenRun(
                            id,
                            EvaluationClaimBarrier.Mode.RETRY,
                            () -> delegate.retryEvaluation(id)
                    );
                }

                @Override
                public int reclaimStaleEvaluating(
                        Long id,
                        LocalDateTime cutoff
                ) {
                    return delegate.reclaimStaleEvaluating(id, cutoff);
                }

                @Override
                public int markEvaluated(Long id) {
                    return delegate.markEvaluated(id);
                }

                @Override
                public int markFailed(Long id, String errorCode) {
                    return delegate.markFailed(id, errorCode);
                }
            };
        }
    }

    static class EvaluationClaimBarrier {

        enum Mode {
            CLAIM,
            RETRY
        }

        private volatile Long answerId;
        private volatile Mode mode;
        private volatile CountDownLatch entered;
        private volatile CountDownLatch start;

        void arm(
                Long targetAnswerId,
                Mode targetMode,
                CountDownLatch enteredLatch,
                CountDownLatch startLatch
        ) {
            answerId = targetAnswerId;
            mode = targetMode;
            entered = enteredLatch;
            start = startLatch;
        }

        void disarm() {
            answerId = null;
            mode = null;
            CountDownLatch resume = start;
            if (resume != null) {
                resume.countDown();
            }
        }

        int awaitThenRun(Long requestedId, Mode requestedMode, IntSupplier cas) {
            if (!armedFor(requestedId, requestedMode)) {
                return cas.getAsInt();
            }

            entered.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "evaluation CAS start latch timed out"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "evaluation CAS wait interrupted",
                        exception
                );
            }
            return cas.getAsInt();
        }

        private boolean armedFor(Long requestedId, Mode requestedMode) {
            return answerId != null
                    && answerId.equals(requestedId)
                    && mode == requestedMode;
        }
    }
}
