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

/** Real MySQL sources and asynchronous report transactions; only model text is replaced. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportFinalConsistencyIntegrationTest extends ReportDatabaseFixture {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectsOneFinalPerMainAndAppliesWeaknessOnce(boolean withFollowUp) {
        long expectedAnswer = answer1;
        if (withFollowUp) expectedAnswer = followUpFinal();
        List<AnswerEvaluation> selected = evaluations.listFinalEffectiveEvaluationsBySessionId(sessionId);
        assertThat(selected).extracting(AnswerEvaluation::getMainInterviewQuestionId).containsExactly(main1, main2);
        assertThat(selected).extracting(AnswerEvaluation::getEvaluationPhase).containsOnly(EvaluationPhase.FINAL);
        assertThat(selected.getFirst().getAnswerId()).isEqualTo(expectedAnswer);

        InterviewReport report = acceptAndConsume();
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
        Throwable failure = catchThrowable(() -> acceptAndConsume());
        System.out.printf("B3 %s: failure=%s, status=%s, report=%s, generatorCalls=%d%n", corruption,
                failure == null ? "NONE" : failure.getClass().getSimpleName(),
                sessions.getInterviewSessionById(sessionId).getReportStatus(),
                reports.getBySessionId(sessionId) != null, generatorCalls.get());
        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.NOT_STARTED);
        assertThat(reports.getBySessionId(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
        assertThat(generatorCalls.get()).isZero();
        assertThat(rawEvaluations()).isEqualTo(before);
    }

    @Test
    void missingFinalFailsRepeatedlyAndRetrySucceedsOnlyAfterValidFinalExists() {
        initial(answer1);
        for (int i = 0; i < 2; i++) {
            assertThat(catchThrowable(() -> acceptAndConsume())).isInstanceOf(IllegalStateException.class);
            assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.NOT_STARTED);
            assertThat(reports.getBySessionId(sessionId)).isNull();
        }
        // Test fixture repair, not an application data migration.
        followUpFinal();
        assertReadyReplayUnchanged(acceptAndConsume());
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
        assertThat(catchThrowable(() -> acceptAndConsume())).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.valueOf(status));
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
        var execution = transactions.claimTaskExecution(accept());
        assertThat(catchThrowable(() -> transactions.completeReportGeneration(execution, invalid)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(reports.getBySessionId(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        // Same recovery CAS used by the service after a failed completion transaction.
        transactions.failExecution(execution, "TEST_INSERT_FAILED");
        assertReadyReplayUnchanged(acceptAndConsume());
    }

}
