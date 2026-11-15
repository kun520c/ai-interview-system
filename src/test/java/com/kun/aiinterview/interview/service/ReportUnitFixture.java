package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.entity.InterviewReportTask.*;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.mapper.*;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.report.async.*;
import com.kun.aiinterview.user.service.UserWeaknessService;
import org.springframework.beans.factory.ObjectProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Mockito-only fixture. No Spring context, datasource, MQ connection or external model. */
class ReportUnitFixture {
    final InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
    final InterviewReportMapper reports = mock(InterviewReportMapper.class);
    final InterviewReportTaskMapper tasks = mock(InterviewReportTaskMapper.class);
    final AnswerEvaluationMapper evaluations = mock(AnswerEvaluationMapper.class);
    final InterviewQuestionMapper questions = mock(InterviewQuestionMapper.class);
    final InterviewAnswerMapper answers = mock(InterviewAnswerMapper.class);
    final UserWeaknessService weaknesses = mock(UserWeaknessService.class);
    final InterviewReportGenerator generator = mock(InterviewReportGenerator.class);
    @SuppressWarnings("unchecked") final ObjectProvider<InterviewReportGenerator> provider = mock(ObjectProvider.class);
    final ReportAsyncProperties properties = new ReportAsyncProperties();
    final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    final InterviewReportFinalReader reader = new InterviewReportFinalReader(evaluations, questions, answers);
    final InterviewReportTransactionService transactions = new InterviewReportTransactionService(reports, sessions,
            tasks, weaknesses, reader, properties, provider, events);
    final LocalDateTime now = LocalDateTime.of(2026, 11, 14, 12, 0);
    final InterviewSession session = InterviewSession.builder().id(2L).userId(1L).version(4)
            .status(InterviewSessionStatus.COMPLETED).reportStatus(InterviewReportStatus.GENERATING)
            .plannedQuestionCount(1).build();
    final InterviewReportTask task = new InterviewReportTask();
    final ReportTaskMessage message = new ReportTaskMessage(3L, 2L, 3L);
    final ReportExecution execution = new ReportExecution(message, 4, session);
    final InterviewReport report = InterviewReport.builder().sessionId(2L).overallScore(new BigDecimal("60.00"))
            .result("FAIR").summary("summary").strengths("[]").weaknesses("[]").suggestions("[]")
            .llmModel("test").promptVersion("test").build();
    final AnswerEvaluation evaluation = AnswerEvaluation.builder().id(7L).answerId(6L).mainInterviewQuestionId(5L)
            .evaluationPhase(EvaluationPhase.FINAL).decisionAction(DecisionAction.FINISH).totalScore(60).build();

    ReportUnitFixture() {
        properties.setEnabled(true);
        task.setId(3L); task.setSessionId(2L); task.setAttemptVersion(3L); task.setExecutionVersion(4L);
        task.setTaskState(State.RUNNING); task.setDeliveryState(Delivery.PENDING);
        task.setQueueDeadline(now.plusMinutes(10)); task.setExecutionDeadline(now.plusMinutes(10));
        lenient().when(sessions.getReportVersionForUpdate(2L)).thenAnswer(i -> session.getVersion().longValue());
        lenient().when(sessions.getReportOwnerId(2L)).thenReturn(1L);
        lenient().when(sessions.getReportSession(2L)).thenReturn(session);
        lenient().when(sessions.getInterviewSessionById(2L)).thenReturn(session);
        lenient().when(tasks.getByIdForUpdate(3L)).thenReturn(task);
        lenient().when(tasks.databaseNow()).thenReturn(now);
        lenient().when(evaluations.listFinalEffectiveEvaluationsBySessionId(2L)).thenReturn(List.of(evaluation));
        lenient().when(questions.listBySessionId(2L)).thenReturn(List.of(InterviewQuestion.builder()
                .id(5L).sessionId(2L).planOrder(1).questionType(InterviewQuestionType.MAIN)
                .status(InterviewQuestionStatus.ANSWERED).build()));
        lenient().when(answers.listByInterviewQuestionIds(List.of(5L))).thenReturn(List.of(InterviewAnswer.builder()
                .id(6L).interviewQuestionId(5L).status(InterviewAnswerStatus.EVALUATED).build()));
        lenient().when(provider.getIfAvailable()).thenReturn(generator);
        lenient().when(generator.generate(any(), any(), any())).thenReturn(new InterviewReportGenerationResult(
                "FAIR", "summary", List.of(), List.of(), List.of(), "test", "test"));
    }

    void successfulCompletion() {
        when(reports.insertReport(any())).thenAnswer(i -> { ((InterviewReport) i.getArgument(0)).setId(8L); return 1; });
        when(sessions.markReportReady(eq(2L), eq(4), any())).thenReturn(1);
        when(tasks.finishSuccess(3L, 4)).thenReturn(1);
    }

    void successfulFailure() {
        when(sessions.markReportFailed(2L, 4)).thenReturn(1);
        when(tasks.finishFailure(eq(3L), eq(State.RUNNING), anyString())).thenReturn(1);
    }

    InterviewReportService worker(InterviewReportTransactionService tx) {
        return new InterviewReportService(sessions, reports, tx, provider, new ObjectMapper(), reader);
    }
}
