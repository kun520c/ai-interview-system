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

/** Real MySQL + real report/weakness transactions; only the text generator is replaced. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportFinalConsistencyIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AnswerEvaluationMapper evaluations;
    @Autowired private InterviewSessionMapper sessions;
    @Autowired private InterviewReportMapper reports;
    @Autowired private InterviewReportTransactionService transactions;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InterviewQuestionMapper questions;
    @Autowired private InterviewAnswerMapper answers;

    private long userId;
    private long questionId;
    private long sessionId;
    private long main1;
    private long main2;
    private long answer1;
    private long answer2;
    private final AtomicInteger generatorCalls = new AtomicInteger();
    private List<AnswerEvaluation> generatorInput;
    private InterviewReportService service;

    @BeforeEach
    void setUp() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("ai_interview_test");
        String unique = "b3-" + UUID.randomUUID();
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
        @SuppressWarnings("unchecked")
        ObjectProvider<InterviewReportGenerator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn((session, rows, score) -> {
            generatorCalls.incrementAndGet();
            generatorInput = rows;
            return new InterviewReportGenerationResult("FAIR", "B3 summary", List.of(), List.of(),
                    List.of(), "deterministic-test", "b3-test");
        });
        service = new InterviewReportService(sessions, reports, evaluations, transactions, questions, answers,
                provider, objectMapper, new StaleRecoveryProperties());
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
        jdbc.update("DELETE FROM interview_session WHERE user_id=?", userId);
        jdbc.update("DELETE FROM `user` WHERE id=?", userId);
        if (questionId != 0) jdbc.update("DELETE FROM question WHERE id=?", questionId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectsOneFinalPerMainAndAppliesWeaknessOnce(boolean withFollowUp) {
        long expectedAnswer = answer1;
        if (withFollowUp) expectedAnswer = followUpFinal();
        List<AnswerEvaluation> selected = evaluations.listFinalEffectiveEvaluationsBySessionId(sessionId);
        assertThat(selected).extracting(AnswerEvaluation::getMainInterviewQuestionId).containsExactly(main1, main2);
        assertThat(selected).extracting(AnswerEvaluation::getEvaluationPhase).containsOnly(EvaluationPhase.FINAL);
        assertThat(selected.getFirst().getAnswerId()).isEqualTo(expectedAnswer);

        InterviewReport report = service.generateReport(userId, sessionId);
        assertThat(report.getOverallScore()).isEqualByComparingTo(withFollowUp ? "75.00" : "70.00");
        assertThat(generatorInput).extracting(AnswerEvaluation::getId)
                .containsExactlyElementsOf(selected.stream().map(AnswerEvaluation::getId).toList());
        Map<String, Object> weakness = weakness();
        assertThat((BigDecimal) weakness.get("weakness_score"))
                .isEqualByComparingTo(withFollowUp ? "25.00" : "30.00");
        assertThat(((Number) weakness.get("discovered_count")).longValue()).isEqualTo(1);
        assertReadyReplayUnchanged(report);
    }

    @ParameterizedTest
    @ValueSource(strings = {"initial-only", "missing-followup-final", "missing-evaluation", "swapped-main", "foreign-answer",
            "wrong-followup-parent", "wrong-followup-session", "main-final-with-followup",
            "duplicate-followup", "unfinished-answer", "plan-count-too-small", "final-followup-action",
            "followup-without-initial", "balanced-duplicate-final", "extra-foreign-final", "unanswered-question", "wrong-plan-order"})
    void rejectsInvalidPersistedDataWithoutReportOrWeaknessWrites(String corruption) {
        switch (corruption) {
            case "initial-only" -> initial(answer1);
            case "missing-followup-final" -> {
                initial(answer1);
                answer(followUp(main1, 3));
            }
            case "missing-evaluation" -> jdbc.update("DELETE FROM answer_evaluation WHERE answer_id=?", answer1);
            case "swapped-main" -> {
                jdbc.update("UPDATE answer_evaluation SET main_interview_question_id=? WHERE answer_id=?", main2, answer1);
                jdbc.update("UPDATE answer_evaluation SET main_interview_question_id=? WHERE answer_id=?", main1, answer2);
            }
            case "foreign-answer" -> {
                jdbc.update("DELETE FROM answer_evaluation WHERE answer_id=?", answer1);
                evaluation(answer(main(session(1), 1)), main1, "FINAL", 30);
            }
            case "wrong-followup-parent" -> {
                long followAnswer = followUpFinal();
                jdbc.update("UPDATE interview_question SET parent_question_id=? WHERE id=(SELECT interview_question_id FROM interview_answer WHERE id=?)", main2, followAnswer);
            }
            case "wrong-followup-session" -> {
                long followAnswer = followUpFinal();
                jdbc.update("UPDATE interview_question SET session_id=? WHERE id=(SELECT interview_question_id FROM interview_answer WHERE id=?)", session(1), followAnswer);
            }
            case "main-final-with-followup" -> followUp(main1, 3);
            case "duplicate-followup" -> {
                followUpFinal();
                evaluation(answer(followUp(main1, 4)), main1, "FINAL", 40);
            }
            case "balanced-duplicate-final" -> {
                jdbc.update("DELETE FROM answer_evaluation WHERE answer_id=?", answer2);
                evaluation(answer(main(session(1), 1)), main1, "FINAL", 30);
            }
            case "extra-foreign-final" -> evaluation(answer(main(session(1), 1)), main1, "FINAL", 30);
            case "unanswered-question" -> jdbc.update("UPDATE interview_question SET status='PENDING' WHERE id=?", main1);
            case "wrong-plan-order" -> jdbc.update("UPDATE interview_question SET plan_order=3 WHERE id=?", main2);
            case "unfinished-answer" -> jdbc.update("UPDATE interview_answer SET status='FAILED' WHERE id=?", answer1);
            case "plan-count-too-small" -> main(sessionId, 3);
            case "final-followup-action" -> jdbc.update("UPDATE answer_evaluation SET decision_action='FOLLOW_UP' WHERE answer_id=?", answer1);
            case "followup-without-initial" -> {
                followUpFinal();
                jdbc.update("DELETE FROM answer_evaluation WHERE answer_id=?", answer1);
            }
            default -> throw new AssertionError(corruption);
        }
        List<Map<String, Object>> before = rawEvaluations();
        Throwable failure = catchThrowable(() -> service.generateReport(userId, sessionId));
        System.out.printf("B3 %s: failure=%s, status=%s, report=%s, generatorCalls=%d%n", corruption,
                failure == null ? "NONE" : failure.getClass().getSimpleName(),
                sessions.getInterviewSessionById(sessionId).getReportStatus(),
                reports.getBySessionId(sessionId) != null, generatorCalls.get());
        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.FAILED);
        assertThat(reports.getBySessionId(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
        assertThat(generatorCalls.get()).isZero();
        assertThat(rawEvaluations()).isEqualTo(before);
    }

    @Test
    void missingFinalFailsRepeatedlyAndRetrySucceedsOnlyAfterValidFinalExists() {
        initial(answer1);
        for (int i = 0; i < 2; i++) {
            assertThat(catchThrowable(() -> service.generateReport(userId, sessionId))).isInstanceOf(IllegalStateException.class);
            assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.FAILED);
            assertThat(reports.getBySessionId(sessionId)).isNull();
        }
        // Test fixture repair, not an application data migration.
        followUpFinal();
        assertReadyReplayUnchanged(service.generateReport(userId, sessionId));
        assertThat(generatorCalls.get()).isEqualTo(1);
    }

    @Test
    void historicalReadyWithInitialOnlyRemainsReadableWithoutRecalculation() {
        initial(answer1);
        InterviewReport historical = InterviewReport.builder().sessionId(sessionId)
                .overallScore(new BigDecimal("42.00")).result("WEAK").summary("historical")
                .strengths("[]").weaknesses("[]").suggestions("[]").llmModel("historical")
                .promptVersion("old").build();
        assertThat(reports.insertReport(historical)).isEqualTo(1);
        jdbc.update("UPDATE interview_session SET report_status='READY', total_score=42 WHERE id=?", sessionId);
        assertReadyReplayUnchanged(historical);
        assertThat(generatorCalls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "GENERATING"})
    void existingUnreadyReportCannotPromoteInvalidSources(String status) {
        initial(answer1);
        InterviewReport historical = InterviewReport.builder().sessionId(sessionId)
                .overallScore(new BigDecimal("42.00")).result("WEAK").summary("historical")
                .strengths("[]").weaknesses("[]").suggestions("[]").llmModel("historical")
                .promptVersion("old").build();
        reports.insertReport(historical);
        jdbc.update("UPDATE interview_session SET report_status=?, updated_at=DATE_SUB(NOW(), INTERVAL 20 MINUTE) WHERE id=?", status, sessionId);
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM interview_report WHERE session_id=?", sessionId);
        assertThat(catchThrowable(() -> service.generateReport(userId, sessionId))).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.FAILED);
        assertThat(jdbc.queryForMap("SELECT * FROM interview_report WHERE session_id=?", sessionId)).isEqualTo(before);
        assertThat(generatorCalls.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
    }

    @Test
    void reportInsertFailureRollsBackAndAllowsFailedRetry() {
        InterviewReport invalid = InterviewReport.builder().sessionId(sessionId)
                .overallScore(new BigDecimal("70.00")).result("FAIR").summary(null)
                .strengths("[]").weaknesses("[]").suggestions("[]").llmModel("test")
                .promptVersion("test").build();
        jdbc.update("UPDATE interview_session SET report_status='GENERATING' WHERE id=?", sessionId);
        assertThat(catchThrowable(() -> transactions.completeReportGeneration(invalid,
                sessions.getInterviewSessionById(sessionId),
                evaluations.listFinalEffectiveEvaluationsBySessionId(sessionId))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(reports.getBySessionId(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        // Same recovery CAS used by the service after a failed completion transaction.
        assertThat(sessions.markReportFailed(sessionId, 0)).isEqualTo(1);
        assertReadyReplayUnchanged(service.generateReport(userId, sessionId));
    }

    private void assertReadyReplayUnchanged(InterviewReport report) {
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

    private long followUpFinal() {
        initial(answer1);
        long id = answer(followUp(main1, 3));
        evaluation(id, main1, "FINAL", 70);
        return id;
    }

    private void initial(long answer) {
        jdbc.update("UPDATE answer_evaluation SET evaluation_phase='INITIAL', decision_action='FOLLOW_UP', total_score=20, correctness_score=4, completeness_score=4, depth_score=4, clarity_score=4, practice_score=4 WHERE answer_id=?", answer);
    }

    private long session(int planned) {
        return insert("""
                INSERT INTO interview_session (user_id, difficulty, status, planned_question_count,
                completed_question_count, report_status, version, started_at, ended_at)
                VALUES (?, 'MEDIUM', 'COMPLETED', ?, ?, 'NOT_STARTED', 0, NOW(), NOW())
                """, userId, planned, planned);
    }

    private long main(long session, int order) {
        return insert("""
                INSERT INTO interview_question (session_id, question_id, category, knowledge_point,
                question_content, question_type, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', 'main snapshot', 'MAIN', ?, ?, 'ANSWERED')
                """, session, questionId, order, order);
    }

    private long followUp(long parent, int display) {
        return insert("""
                INSERT INTO interview_question (session_id, category, knowledge_point, question_content,
                question_type, parent_question_id, display_order, status)
                VALUES (?, 'JAVA_COLLECTION', 'followup-only', 'follow up', 'FOLLOW_UP', ?, ?, 'ANSWERED')
                """, sessionId, parent, display);
    }

    private long answer(long question) {
        return insert("INSERT INTO interview_answer (interview_question_id, answer_content, status, request_id) VALUES (?, 'answer', 'EVALUATED', ?)",
                question, "b3-" + UUID.randomUUID());
    }

    private void evaluation(long answer, long main, String phase, int score) {
        insert("""
                INSERT INTO answer_evaluation (answer_id, main_interview_question_id, evaluation_phase,
                correctness_score, completeness_score, depth_score, clarity_score, practice_score,
                total_score, level, strengths, missing_points, scoring_point_results, follow_up_recommended,
                decision_action, llm_model, prompt_version, evaluation_standard_version, retrieval_batch_id, raw_result)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'FAIR', '[]', '[]', '[]', false,
                'FINISH', 'test', 'test', 'test', ?, '{}')
                """, answer, main, phase, score / 5, score / 5, score / 5, score / 5, score / 5, score, "b3-" + UUID.randomUUID());
    }

    private Map<String, Object> weakness() {
        return jdbc.queryForMap("SELECT * FROM user_weakness WHERE user_id=?", userId);
    }

    private List<Map<String, Object>> rawEvaluations() {
        return jdbc.queryForList("SELECT * FROM answer_evaluation WHERE main_interview_question_id IN (?, ?) ORDER BY id", main1, main2);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }
}
