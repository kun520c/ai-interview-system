package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InterviewReportServiceTest {
    ReportUnitFixture f;
    InterviewReportTransactionService tx;
    InterviewReportService service;
    @BeforeEach void setup() { f = new ReportUnitFixture(); tx = mock(InterviewReportTransactionService.class); service = f.worker(tx); }

    @Test void shouldRejectNullIdentifiersBeforeMapperCalls() {
        assertThatThrownBy(() -> service.generateReportForApi(null, 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generateReportForApi(1L, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tx);
    }

    @Test void postOnlyAcceptsAndDoesNotGenerate() {
        when(tx.acceptReportTask(1L, 2L)).thenReturn(InterviewReportStatus.GENERATING);
        assertThat(service.generateReportForApi(1L, 2L).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        verifyNoInteractions(f.generator);
    }

    @Test void claimedExecutionBuildsReportFromFinalAndCommitsWithCapturedToken() {
        service.generateClaimedReport(f.execution);
        var captor = org.mockito.ArgumentCaptor.forClass(InterviewReport.class);
        verify(tx).completeReportGeneration(eq(f.execution), captor.capture());
        assertThat(captor.getValue().getOverallScore()).isEqualByComparingTo("60.00");
        assertThat(captor.getValue().getStrengths()).isEqualTo("[]");
        verify(f.generator).generate(eq(f.session), eq(List.of(f.evaluation)), eq(new BigDecimal("60.00")));
    }

    @Test void historicalReportAvoidsModelAndLeavesReconciliationToTransaction() {
        when(f.reports.getBySessionId(2L)).thenReturn(f.report);
        service.generateClaimedReport(f.execution);
        verify(tx).completeReportGeneration(f.execution, f.report);
        verifyNoInteractions(f.generator, f.weaknesses);
    }

    @Test void deterministicModelFailureAcknowledgesOnlyAfterFailurePersistence() {
        when(f.generator.generate(any(), any(), any())).thenThrow(new IllegalStateException("invalid JSON"));
        service.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "GENERATION_FAILED");
        verify(tx, never()).completeReportGeneration(any(), any());
    }

    @Test void failureWriteFailureEscapesForMqRetry() {
        when(f.generator.generate(any(), any(), any())).thenThrow(new IllegalArgumentException());
        var db = new DataAccessResourceFailureException("unavailable");
        doThrow(db).when(tx).failExecution(any(), any());
        assertThatThrownBy(() -> service.generateClaimedReport(f.execution)).isSameAs(db);
    }

    @Test void uncertainCommitMustNotAcknowledgeOrTryToFailNewVersion() {
        var uncertain = new TransactionSystemException("commit outcome unknown");
        doThrow(uncertain).when(tx).completeReportGeneration(any(), any());
        assertThatThrownBy(() -> service.generateClaimedReport(f.execution)).isSameAs(uncertain);
        verify(tx, never()).failExecution(any(), any());
    }

    @Test void databaseReadFailureMustNotAcknowledge() {
        when(f.evaluations.listFinalEffectiveEvaluationsBySessionId(2L)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.generateClaimedReport(f.execution)).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(tx, f.generator);
    }

    @Test void deterministicCompletionRejectionRequiresFailureWrite() {
        doThrow(new IllegalStateException("FINAL changed")).when(tx).completeReportGeneration(any(), any());
        service.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "COMPLETION_REJECTED");
    }

    @Test void missingGeneratorPersistsFailureWithoutAnotherExecution() {
        when(f.provider.getIfAvailable()).thenReturn(null);
        service.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "GENERATION_FAILED");
    }

    @Test void nullGeneratorResultPersistsFailure() {
        when(f.generator.generate(any(), any(), any())).thenReturn(null);
        service.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "GENERATION_FAILED");
    }

    @Test void jsonSerializationFailurePersistsFailure() throws Exception {
        var mapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("invalid") {});
        var failing = new InterviewReportService(f.sessions, f.reports, tx, f.provider, mapper, f.reader);
        failing.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "GENERATION_FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "count", "initial", "main-null", "decision", "score-null", "missing-main", "wrong-session",
            "unanswered", "wrong-order", "missing-answer", "unfinished-answer", "wrong-answer", "duplicate-final"})
    void finalInvariantRejectsInvalidData(String corruption) {
        switch (corruption) {
            case "empty" -> when(f.evaluations.listFinalEffectiveEvaluationsBySessionId(2L)).thenReturn(List.of());
            case "count" -> f.session.setPlannedQuestionCount(2);
            case "initial" -> f.evaluation.setEvaluationPhase(EvaluationPhase.INITIAL);
            case "main-null" -> f.evaluation.setMainInterviewQuestionId(null);
            case "decision" -> f.evaluation.setDecisionAction(DecisionAction.FOLLOW_UP);
            case "score-null" -> f.evaluation.setTotalScore(null);
            case "missing-main" -> when(f.questions.listBySessionId(2L)).thenReturn(List.of());
            case "wrong-session", "unanswered", "wrong-order" -> {
                var question = f.questions.listBySessionId(2L).getFirst();
                if (corruption.equals("wrong-session")) question.setSessionId(99L);
                if (corruption.equals("unanswered")) question.setStatus(InterviewQuestionStatus.PENDING);
                if (corruption.equals("wrong-order")) question.setPlanOrder(2);
            }
            case "missing-answer" -> when(f.answers.listByInterviewQuestionIds(any())).thenReturn(List.of());
            case "unfinished-answer" -> f.answers.listByInterviewQuestionIds(List.of(5L)).getFirst().setStatus(InterviewAnswerStatus.FAILED);
            case "wrong-answer" -> f.evaluation.setAnswerId(99L);
            case "duplicate-final" -> {
                f.session.setPlannedQuestionCount(2);
                when(f.evaluations.listFinalEffectiveEvaluationsBySessionId(2L)).thenReturn(List.of(f.evaluation, f.evaluation));
            }
            default -> throw new AssertionError(corruption);
        }
        assertThatThrownBy(() -> f.reader.aggregateFinalEvaluations(f.session)).isInstanceOf(IllegalStateException.class);
        service.generateClaimedReport(f.execution);
        verify(tx).failExecution(f.execution, "GENERATION_FAILED");
        verifyNoInteractions(f.generator);
    }

    @Test void averageUsesHalfUpAndDoesNotUseModelScore() {
        f.session.setPlannedQuestionCount(3);
        var questions = java.util.stream.IntStream.range(0,3).mapToObj(i -> InterviewQuestion.builder().id(5L+i).sessionId(2L)
                .planOrder(i+1).questionType(InterviewQuestionType.MAIN).status(InterviewQuestionStatus.ANSWERED).build()).toList();
        var answers = java.util.stream.IntStream.range(0,3).mapToObj(i -> InterviewAnswer.builder().id(15L+i)
                .interviewQuestionId(5L+i).status(InterviewAnswerStatus.EVALUATED).build()).toList();
        var evaluations = java.util.stream.IntStream.range(0,3).mapToObj(i -> AnswerEvaluation.builder().mainInterviewQuestionId(5L+i)
                .answerId(15L+i).evaluationPhase(EvaluationPhase.FINAL).decisionAction(DecisionAction.FINISH).totalScore(i==0?81:80).build()).toList();
        when(f.questions.listBySessionId(2L)).thenReturn(questions);
        when(f.answers.listByInterviewQuestionIds(List.of(5L,6L,7L))).thenReturn(answers);
        when(f.evaluations.listFinalEffectiveEvaluationsBySessionId(2L)).thenReturn(evaluations);
        assertThat(f.reader.aggregateFinalEvaluations(f.session).overallScore()).isEqualByComparingTo("80.33");
    }
}
