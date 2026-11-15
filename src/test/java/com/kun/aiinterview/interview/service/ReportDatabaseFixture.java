package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class ReportDatabaseFixture {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected AnswerEvaluationMapper evaluations;
    @Autowired protected InterviewSessionMapper sessions;
    @Autowired protected InterviewReportMapper reports;
    @Autowired protected InterviewReportTransactionService transactions;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected InterviewQuestionMapper questions;
    @Autowired protected InterviewAnswerMapper answers;

    protected long userId;
    protected long questionId;
    protected long sessionId;
    protected long main1;
    protected long main2;
    protected long answer1;
    protected long answer2;
    protected final AtomicInteger generatorCalls = new AtomicInteger();
    protected List<AnswerEvaluation> generatorInput;
    protected InterviewReportService service;

    @Autowired protected com.kun.aiinterview.interview.mapper.InterviewReportTaskMapper tasks;
    @Autowired protected InterviewReportFinalReader finalReader;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected InterviewReportGenerator configuredGenerator;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected com.kun.aiinterview.interview.report.async.ReportAsyncProperties asyncProperties;
    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("ai_interview_test");
        String unique = "m1b-" + UUID.randomUUID();
        userId = insert("INSERT INTO `user` (account, username, password, email) VALUES (?, ?, ?, ?)",
                unique, "B3 test", "test-hash", unique + "@example.com");
        questionId = insert("""
                INSERT INTO question (category, knowledge_point, difficulty, question_content, reference_answer)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', 'HashMap?', 'reference')
                """, unique);
        sessionId = session(2);
        main1 = main(sessionId, 1);
        main2 = main(sessionId, 2);
        answer1 = answer(main1);
        answer2 = answer(main2);
        evaluation(answer1, main1, "FINAL", 60);
        evaluation(answer2, main2, "FINAL", 80);
        generatorCalls.set(0);
        when(asyncProperties.isEnabled()).thenReturn(true);
        when(asyncProperties.isAccepting()).thenReturn(true);
        when(asyncProperties.getQueueDeadlineSeconds()).thenReturn(600);
        when(asyncProperties.getExecutionDeadlineSeconds()).thenReturn(600);
        when(asyncProperties.getMaxSendAttempts()).thenReturn(20);
        when(configuredGenerator.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).as("model call must be outside a transaction").isFalse();
            generatorCalls.incrementAndGet();
            generatorInput = invocation.getArgument(1);
            return new InterviewReportGenerationResult("FAIR", "B3 summary", List.of(), List.of(), List.of(), "test", "test");
        });
        @SuppressWarnings("unchecked") ObjectProvider<InterviewReportGenerator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(configuredGenerator);
        service = new InterviewReportService(sessions, reports, transactions, provider, objectMapper, finalReader);
    }

    @AfterEach
    void cleanUpOnlyThisFixture() {
        if (userId == 0) return;
        jdbc.update("DELETE FROM user_weakness WHERE user_id = ?", userId);
        jdbc.update("DELETE r FROM interview_report r JOIN interview_session s ON s.id=r.session_id WHERE s.user_id=?", userId);
        jdbc.update("""
                DELETE e FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id
                JOIN interview_question q ON q.id=a.interview_question_id
                JOIN interview_session s ON s.id=q.session_id WHERE s.user_id=?
                """, userId);
        jdbc.update("""
                DELETE a FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id
                JOIN interview_session s ON s.id=q.session_id WHERE s.user_id=?
                """, userId);
        jdbc.update("UPDATE interview_session SET current_interview_question_id=NULL WHERE user_id=?", userId);
        jdbc.update("DELETE q FROM interview_question q JOIN interview_session s ON s.id=q.session_id WHERE s.user_id=? AND q.question_type='FOLLOW_UP'", userId);
        jdbc.update("DELETE q FROM interview_question q JOIN interview_session s ON s.id=q.session_id WHERE s.user_id=?", userId);
        jdbc.update("DELETE t FROM interview_report_task t JOIN interview_session s ON s.id=t.session_id WHERE s.user_id=?", userId);
        jdbc.update("DELETE FROM interview_session WHERE user_id=?", userId);
        jdbc.update("DELETE FROM `user` WHERE id=?", userId);
        if (questionId != 0) jdbc.update("DELETE FROM question WHERE id=?", questionId);
    }

    protected void assertReadyReplayUnchanged(InterviewReport report) {
        Map<String, Object> sessionBefore = jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?", sessionId);
        Map<String, Object> reportBefore = jdbc.queryForMap("SELECT * FROM interview_report WHERE session_id=?", sessionId);
        List<Map<String, Object>> weaknessBefore = jdbc.queryForList("SELECT * FROM user_weakness WHERE user_id=?", userId);
        for (int i = 0; i < 2; i++) {
            assertThat(service.getReport(userId, sessionId).overallScore()).isEqualByComparingTo(report.getOverallScore());
            assertThat(service.generateReportForApi(userId, sessionId).overallScore()).isEqualByComparingTo(report.getOverallScore());
        }
        assertThat(jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?", sessionId)).isEqualTo(sessionBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM interview_report WHERE session_id=?", sessionId)).isEqualTo(reportBefore);
        assertThat(jdbc.queryForList("SELECT * FROM user_weakness WHERE user_id=?", userId)).isEqualTo(weaknessBefore);
    }

    protected long followUpFinal() {
        initial(answer1);
        long id = answer(followUp(main1, 3));
        evaluation(id, main1, "FINAL", 70);
        return id;
    }

    protected void initial(long answer) {
        jdbc.update("UPDATE answer_evaluation SET evaluation_phase='INITIAL', decision_action='FOLLOW_UP', total_score=20, correctness_score=4, completeness_score=4, depth_score=4, clarity_score=4, practice_score=4 WHERE answer_id=?", answer);
    }

    protected long session(int planned) {
        return insert("""
                INSERT INTO interview_session (user_id, difficulty, status, planned_question_count,
                completed_question_count, report_status, version, started_at, ended_at)
                VALUES (?, 'MEDIUM', 'COMPLETED', ?, ?, 'NOT_STARTED', 0, NOW(), NOW())
                """, userId, planned, planned);
    }

    protected long main(long session, int order) {
        return insert("""
                INSERT INTO interview_question (session_id, question_id, category, knowledge_point,
                question_content, question_type, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', 'main snapshot', 'MAIN', ?, ?, 'ANSWERED')
                """, session, questionId, order, order);
    }

    protected long followUp(long parent, int display) {
        return insert("""
                INSERT INTO interview_question (session_id, category, knowledge_point, question_content,
                question_type, parent_question_id, display_order, status)
                VALUES (?, 'JAVA_COLLECTION', 'followup-only', 'follow up', 'FOLLOW_UP', ?, ?, 'ANSWERED')
                """, sessionId, parent, display);
    }

    protected long answer(long question) {
        return insert("INSERT INTO interview_answer (interview_question_id, answer_content, status, request_id) VALUES (?, 'answer', 'EVALUATED', ?)",
                question, "b3-" + UUID.randomUUID());
    }

    protected void evaluation(long answer, long main, String phase, int score) {
        insert("""
                INSERT INTO answer_evaluation (answer_id, main_interview_question_id, evaluation_phase,
                correctness_score, completeness_score, depth_score, clarity_score, practice_score,
                total_score, level, strengths, missing_points, scoring_point_results, follow_up_recommended,
                decision_action, llm_model, prompt_version, evaluation_standard_version, retrieval_batch_id, raw_result)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'FAIR', '[]', '[]', '[]', false,
                'FINISH', 'test', 'test', 'test', ?, '{}')
                """, answer, main, phase, score / 5, score / 5, score / 5, score / 5, score / 5, score, "b3-" + UUID.randomUUID());
    }

    protected Map<String, Object> weakness() {
        return jdbc.queryForMap("SELECT * FROM user_weakness WHERE user_id=?", userId);
    }

    protected List<Map<String, Object>> rawEvaluations() {
        return jdbc.queryForList("SELECT * FROM answer_evaluation WHERE main_interview_question_id IN (?, ?) ORDER BY id", main1, main2);
    }

    protected long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
    protected com.kun.aiinterview.interview.report.async.ReportTaskMessage accept() {
        assertThat(service.generateReportForApi(userId, sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        var active = tasks.listActiveBySessionId(sessionId);
        assertThat(active).hasSize(1);
        return com.kun.aiinterview.interview.report.async.ReportOutboxDispatcher.message(active.getFirst());
    }

    protected InterviewReport acceptAndConsume() {
        if (service.generateReportForApi(userId, sessionId).reportStatus() != InterviewReportStatus.READY) {
            var active = tasks.listActiveBySessionId(sessionId);
            assertThat(active).hasSize(1);
            var message = com.kun.aiinterview.interview.report.async.ReportOutboxDispatcher.message(active.getFirst());
            new com.kun.aiinterview.interview.report.async.ReportMessageConsumer(transactions, service).onMessage(message);
        }
        return reports.getBySessionId(sessionId);
    }

    protected InterviewReport reportDraft() {
        return InterviewReport.builder().sessionId(sessionId).overallScore(new BigDecimal("70.00"))
                .result("FAIR").summary("B3 summary").strengths("[]").weaknesses("[]").suggestions("[]")
                .llmModel("test").promptVersion("test").build();
    }
}
