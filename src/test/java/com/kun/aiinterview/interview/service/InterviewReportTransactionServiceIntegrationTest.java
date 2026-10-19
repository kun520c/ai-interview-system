package com.kun.aiinterview.interview.service;

import org.mockito.Mockito;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.ParameterizedTest;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.entity.UserWeakness;
import com.kun.aiinterview.user.enums.UserWeaknessStatus;
import com.kun.aiinterview.user.mapper.UserWeaknessMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportTransactionServiceIntegrationTest {

    private static final int SESSION_VERSION = 4;
    private static final String KNOWLEDGE_POINT = "HashMap";

    @Autowired
    private InterviewReportTransactionService transactionService;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private UserWeaknessMapper userWeaknessMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");
        String account = "report-tx-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Report事务测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        userId = requiredId(
                "SELECT id FROM `user` WHERE account = ?",
                account
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, total_score, report_status,
                     version, started_at, ended_at)
                VALUES (?, 'MEDIUM', 'COMPLETED', 1, 1, NULL,
                        'GENERATING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                SESSION_VERSION
        );
        sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM user_weakness WHERE user_id = ?",
                    userId
            );
        }
        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_report WHERE session_id = ?",
                    sessionId
            );
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    userId
            );
        }
    }

    @Test
    void shouldInsertReportWeaknessAndMarkSessionReadyAtomically() {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        InterviewReport report = report();

        InterviewReport completed =
                transactionService.completeReportGeneration(
                        report,
                        session,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                KNOWLEDGE_POINT,
                                60
                        ))
                );

        assertThat(completed.getId()).isNotNull();
        InterviewReport persisted = interviewReportMapper
                .getBySessionId(sessionId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getSessionId()).isEqualTo(sessionId);
        assertThat(persisted.getOverallScore())
                .isEqualByComparingTo("80.33");
        assertThat(persisted.getWeaknesses())
                .isEqualTo("[\"并发知识不足\"]");
        assertThat(persisted.getPromptVersion())
                .isEqualTo("report-v1");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();

        UserWeakness weakness = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(weakness).isNotNull();
        assertThat(weakness.getStatus())
                .isEqualTo(UserWeaknessStatus.ACTIVE);
        assertThat(weakness.getDiscoveredCount()).isEqualTo(1);
        assertThat(weakness.getWeaknessScore())
                .isEqualByComparingTo("40.00");
        assertThat(weakness.getResolvedAt()).isNull();

        InterviewSession ready = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(ready.getReportStatus())
                .isEqualTo(InterviewReportStatus.READY);
        assertThat(ready.getTotalScore())
                .isEqualByComparingTo("80.33");
        assertThat(ready.getVersion())
                .isEqualTo(SESSION_VERSION + 1);
    }

    @Test
    void shouldRollbackReportAndWeaknessWhenMarkReadyFails() {
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        UserWeakness original = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(original.getDiscoveredCount()).isEqualTo(1);

        InterviewSession staleSession = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        staleSession.setVersion(SESSION_VERSION - 1);

        assertThatThrownBy(
                () -> transactionService.completeReportGeneration(
                        report(),
                        staleSession,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                KNOWLEDGE_POINT,
                                50
                        ))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session报告状态更新为READY失败");

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_report WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(reportCount).isZero();

        UserWeakness unchanged = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(unchanged.getId()).isEqualTo(original.getId());
        assertThat(unchanged.getDiscoveredCount()).isEqualTo(1);
        assertThat(unchanged.getWeaknessScore())
                .isEqualByComparingTo("40.00");
        assertThat(unchanged.getStatus())
                .isEqualTo(UserWeaknessStatus.ACTIVE);
        assertThat(unchanged.getLastDiscoveredAt())
                .isEqualTo(original.getLastDiscoveredAt());

        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(session.getReportStatus())
                .isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(session.getTotalScore()).isNull();
        assertThat(session.getVersion())
                .isEqualTo(SESSION_VERSION);
    }

    @Test
    void shouldRollbackReportWhenWeaknessSqlFails() {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        String overlongKnowledgePoint = "x".repeat(101);

        assertThatThrownBy(
                () -> transactionService.completeReportGeneration(
                        report(),
                        session,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                overlongKnowledgePoint,
                                60
                        ))
                )
        ).isInstanceOf(RuntimeException.class);

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_report WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(reportCount).isZero();

        Integer weaknessCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_weakness WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(weaknessCount).isZero();

        InterviewSession unchanged = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(unchanged.getReportStatus())
                .isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(unchanged.getTotalScore()).isNull();
        assertThat(unchanged.getVersion())
                .isEqualTo(SESSION_VERSION);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void oldReportVersionCannotCommitOrFailReplacement(boolean oldThrows, boolean replacementFinishesFirst)
            throws Exception {
        jdbcTemplate.update("UPDATE interview_session SET report_status = 'NOT_STARTED' WHERE id = ?", sessionId);
        var evaluations = List.of(evaluation(QuestionCategory.JAVA_COLLECTION, KNOWLEDGE_POINT, 60));
        var evaluationMapper = Mockito.mock(AnswerEvaluationMapper.class);
        Mockito.when(evaluationMapper.listFinalEffectiveEvaluationsBySessionId(sessionId)).thenReturn(evaluations);
        var provider = Mockito.mock(ObjectProvider.class);
        var oldEntered = new CountDownLatch(1);
        var newEntered = new CountDownLatch(1);
        var releaseOld = new CountDownLatch(1);
        var releaseNew = new CountDownLatch(1);
        var calls = new AtomicInteger();
        InterviewReportGenerator generator = (session, rows, score) -> {
            boolean old = calls.incrementAndGet() == 1;
            (old ? oldEntered : newEntered).countDown();
            try {
                if (!(old ? releaseOld : releaseNew).await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("report latch timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            if (old && oldThrows) { throw new IllegalArgumentException("old report provider failed"); }
            return new InterviewReportGenerationResult(
                    "PASS", old ? "worker-A" : "worker-B", List.of(), List.of(), List.of(), "fake", "fake");
        };
        Mockito.when(provider.getIfAvailable()).thenReturn(generator);
        var service = new InterviewReportService(interviewSessionMapper, interviewReportMapper, evaluationMapper,
                transactionService, provider, new ObjectMapper(),
                new StaleRecoveryProperties());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var old = executor.submit(() -> catchThrowable(() -> service.generateReport(userId, sessionId)));
            assertThat(oldEntered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.update("UPDATE interview_session SET updated_at = ? WHERE id = ?",
                    LocalDateTime.now().minusMinutes(20), sessionId);
            var replacement = executor.submit(() -> service.generateReport(userId, sessionId));
            assertThat(newEntered.await(10, TimeUnit.SECONDS)).isTrue();
            int replacementVersion = interviewSessionMapper.getInterviewSessionById(sessionId).getVersion();
            if (replacementFinishesFirst) {
                releaseNew.countDown();
                assertThat(replacement.get(10, TimeUnit.SECONDS).getSummary()).isEqualTo("worker-B");
            }
            releaseOld.countDown();
            Throwable oldFailure = old.get(10, TimeUnit.SECONDS);
            if (oldThrows) { assertThat(oldFailure).isInstanceOf(IllegalArgumentException.class); }
            else { assertThat(oldFailure).isInstanceOfAny(IllegalStateException.class, DuplicateKeyException.class); }
            if (!replacementFinishesFirst) {
                assertThat(interviewSessionMapper.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
                assertThat(interviewSessionMapper.getInterviewSessionById(sessionId).getVersion()).isEqualTo(replacementVersion);
                assertThat(interviewReportMapper.getBySessionId(sessionId)).isNull();
                releaseNew.countDown();
                assertThat(replacement.get(10, TimeUnit.SECONDS).getSummary()).isEqualTo("worker-B");
            }
            assertThat(interviewReportMapper.getBySessionId(sessionId).getSummary()).isEqualTo("worker-B");
            assertThat(interviewSessionMapper.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.READY);
            assertThat(userWeaknessMapper.getByUserCategoryAndKnowledgePoint(userId, QuestionCategory.JAVA_COLLECTION, KNOWLEDGE_POINT)
                    .getDiscoveredCount()).isEqualTo(1);
            assertThat(service.generateReport(userId, sessionId).getSummary()).isEqualTo("worker-B");
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            releaseOld.countDown();
            releaseNew.countDown();
        }
    }

    private InterviewReport report() {
        return InterviewReport.builder()
                .sessionId(sessionId)
                .overallScore(new BigDecimal("80.33"))
                .result("PASS")
                .summary("总体表现良好")
                .strengths("[\"Java基础扎实\"]")
                .weaknesses("[\"并发知识不足\"]")
                .suggestions("[\"复习线程池\"]")
                .llmModel("report-test-model")
                .promptVersion("report-v1")
                .build();
    }

    @Test
    void simultaneousReportRequestsHaveOneClaimOneReportAndOneWeaknessUpdate() throws Exception {
        jdbcTemplate.update("UPDATE interview_session SET report_status = 'NOT_STARTED' WHERE id = ?", sessionId);
        var snapshotsRead = new CountDownLatch(2);
        var releaseClaim = new CountDownLatch(1);
        var generating = new CountDownLatch(1);
        var releaseGenerator = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var calls = new AtomicInteger();
        var sessions = Mockito.mock(InterviewSessionMapper.class,
                org.mockito.AdditionalAnswers.delegatesTo(interviewSessionMapper));
        Mockito.doAnswer(i -> {
            var snapshot = interviewSessionMapper.getInterviewSessionById(sessionId);
            if (reads.incrementAndGet() <= 2) {
                snapshotsRead.countDown();
                assertThat(releaseClaim.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return snapshot;
        }).when(sessions).getInterviewSessionById(sessionId);
        InterviewReportGenerator generator = (session, rows, score) -> {
            calls.incrementAndGet();
            assertThat(session.getVersion()).isEqualTo(SESSION_VERSION + 1);
            generating.countDown();
            try { assertThat(releaseGenerator.await(10, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            return new InterviewReportGenerationResult("PASS", "single winner", List.of(), List.of(), List.of(), "fake", "fake");
        };
        var service = reportService(sessions, generator);
        var executor = Executors.newFixedThreadPool(2);
        var completion = new java.util.concurrent.ExecutorCompletionService<Throwable>(executor);
        try {
            for (int i = 0; i < 2; i++) {
                completion.submit(() -> catchThrowable(() -> service.generateReport(userId, sessionId)));
            }
            assertThat(snapshotsRead.await(10, TimeUnit.SECONDS)).isTrue();
            releaseClaim.countDown();
            assertThat(generating.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = completion.poll(10, TimeUnit.SECONDS);
            assertThat(loser).isNotNull();
            assertThat(loser.get()).isInstanceOf(com.kun.aiinterview.common.exception.ConflictException.class);
            assertThat(calls.get()).isEqualTo(1);
            releaseGenerator.countDown();
            var winner = completion.poll(10, TimeUnit.SECONDS);
            assertThat(winner).isNotNull();
            assertThat(winner.get()).isNull();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interview_report WHERE session_id = ?",
                    Integer.class, sessionId)).isEqualTo(1);
            assertThat(userWeaknessMapper.getByUserCategoryAndKnowledgePoint(userId,
                    QuestionCategory.JAVA_COLLECTION, KNOWLEDGE_POINT).getDiscoveredCount()).isEqualTo(1);
            assertThat(service.generateReport(userId, sessionId).getSummary()).isEqualTo("single winner");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            releaseClaim.countDown();
            releaseGenerator.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void newRequestRecoversCrashedGeneratingAndAppliesWeaknessOnce() {
        jdbcTemplate.update("UPDATE interview_session SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(11), sessionId);
        var calls = new AtomicInteger();
        var service = reportService(interviewSessionMapper, (session, rows, score) -> {
            calls.incrementAndGet();
            assertThat(session.getVersion()).isEqualTo(SESSION_VERSION + 1);
            return new InterviewReportGenerationResult("PASS", "recovered", List.of(), List.of(), List.of(), "fake", "fake");
        });
        assertThat(service.generateReport(userId, sessionId).getSummary()).isEqualTo("recovered");
        assertThat(service.generateReport(userId, sessionId).getSummary()).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(interviewSessionMapper.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.READY);
        assertThat(userWeaknessMapper.getByUserCategoryAndKnowledgePoint(userId,
                QuestionCategory.JAVA_COLLECTION, KNOWLEDGE_POINT).getDiscoveredCount()).isEqualTo(1);
    }

    private InterviewReportService reportService(InterviewSessionMapper sessions, InterviewReportGenerator generator) {
        var evaluations = Mockito.mock(AnswerEvaluationMapper.class);
        Mockito.when(evaluations.listFinalEffectiveEvaluationsBySessionId(sessionId))
                .thenReturn(List.of(evaluation(QuestionCategory.JAVA_COLLECTION, KNOWLEDGE_POINT, 70)));
        @SuppressWarnings("unchecked")
        ObjectProvider<InterviewReportGenerator> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(generator);
        return new InterviewReportService(sessions, interviewReportMapper, evaluations, transactionService,
                provider, new ObjectMapper(), new StaleRecoveryProperties());
    }

    private AnswerEvaluation evaluation(
            QuestionCategory category,
            String knowledgePoint,
            int totalScore
    ) {
        return AnswerEvaluation.builder()
                .category(category)
                .knowledgePoint(knowledgePoint)
                .totalScore(totalScore)
                .build();
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
}
