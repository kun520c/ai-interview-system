package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.entity.InterviewReportTask.State;
import com.kun.aiinterview.interview.entity.InterviewReportTask.Delivery;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.report.async.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReportTaskTransactionTest {
    ReportUnitFixture f;
    @BeforeEach void setup() { f = new ReportUnitFixture(); }

    @Test void foreignSessionIsRejectedBeforeAnyLock() {
        assertThatThrownBy(() -> f.transactions.acceptReportTask(99L, 2L))
                .isInstanceOf(com.kun.aiinterview.common.exception.ResourceNotFoundException.class);
        verify(f.sessions, never()).getReportVersionForUpdate(any());
        verifyNoInteractions(f.tasks);
    }

    @Test void ownershipIsRecheckedAfterLocking() {
        var changed = com.kun.aiinterview.interview.entity.InterviewSession.builder().id(2L).userId(99L).build();
        when(f.sessions.getReportSession(2L)).thenReturn(changed);
        assertThatThrownBy(() -> f.transactions.acceptReportTask(1L, 2L))
                .isInstanceOf(com.kun.aiinterview.common.exception.ResourceNotFoundException.class);
        verify(f.sessions).getReportVersionForUpdate(2L);
        verifyNoInteractions(f.tasks);
    }

    @Test void locksSessionBeforeTaskAndCommitsTaskLast() {
        f.successfulCompletion();
        f.transactions.completeReportGeneration(f.execution, f.report);
        var order = inOrder(f.sessions, f.tasks, f.reports, f.weaknesses);
        order.verify(f.sessions).getReportVersionForUpdate(2L);
        order.verify(f.sessions).getReportSession(2L);
        order.verify(f.tasks).getByIdForUpdate(3L);
        order.verify(f.reports).insertReport(f.report);
        order.verify(f.weaknesses).applySessionEvaluations(1L, List.of(f.evaluation));
        order.verify(f.sessions).markReportReady(2L, 4, f.report.getOverallScore());
        order.verify(f.tasks).finishSuccess(3L, 4);
    }

    @Test void failedTaskSuccessWriteRollsBackThroughException() {
        f.successfulCompletion(); when(f.tasks.finishSuccess(3L,4)).thenReturn(0);
        assertThatThrownBy(() -> f.transactions.completeReportGeneration(f.execution,f.report)).isInstanceOf(IllegalStateException.class);
    }

    @Test void historicalReportValidatedWithoutSecondWeaknessUpdate() {
        when(f.reports.getBySessionId(2L)).thenReturn(f.report);
        when(f.sessions.markReportReady(2L,4,f.report.getOverallScore())).thenReturn(1);
        when(f.tasks.finishSuccess(3L,4)).thenReturn(1);
        f.transactions.completeReportGeneration(f.execution,f.report);
        verify(f.reports,never()).insertReport(any()); verifyNoInteractions(f.weaknesses);
    }

    @Test void inconsistentHistoricalReportCannotBePromoted() {
        when(f.reports.getBySessionId(2L)).thenReturn(f.report);
        f.report.setOverallScore(new java.math.BigDecimal("42.00"));
        assertThatThrownBy(() -> f.transactions.completeReportGeneration(f.execution,f.report)).isInstanceOf(IllegalStateException.class);
        verify(f.sessions,never()).markReportReady(any(),any(),any()); verifyNoInteractions(f.weaknesses);
    }

    @Test void finalRecheckedAtCommit() {
        f.evaluation.setEvaluationPhase(EvaluationPhase.INITIAL);
        assertThatThrownBy(() -> f.transactions.completeReportGeneration(f.execution,f.report)).isInstanceOf(IllegalStateException.class);
        verify(f.reports,never()).insertReport(any());
    }

    @Test void oldExecutionCannotUseNewerSessionVersion() {
        f.session.setVersion(10);
        when(f.tasks.finishFailure(3L,State.RUNNING,"STALE_ATTEMPT")).thenReturn(1);
        f.transactions.completeReportGeneration(f.execution,f.report);
        verify(f.reports,never()).insertReport(any());
        verify(f.sessions,never()).markReportReady(any(),any(),any());
        verify(f.sessions,never()).markReportFailed(any(),any());
    }

    @Test void expiredExecutionCannotCommitEvenBeforeScannerRuns() {
        f.task.setExecutionDeadline(f.now); f.successfulFailure();
        f.transactions.completeReportGeneration(f.execution,f.report);
        verify(f.tasks).finishFailure(3L,State.RUNNING,"EXECUTION_EXPIRED");
        verify(f.reports,never()).insertReport(any());
    }

    @ParameterizedTest @ValueSource(strings = {"SUCCEEDED", "FAILED"})
    void committedTerminalIsSafeDuplicate(String state) {
        f.task.setTaskState(State.valueOf(state));
        assertThat(f.transactions.claimTaskExecution(f.message)).isNull();
        f.transactions.completeReportGeneration(f.execution,f.report);
        f.transactions.failExecution(f.execution,"GENERATION_FAILED");
        verify(f.reports,never()).insertReport(any()); verify(f.sessions,never()).markReportFailed(any(),any());
    }

    @Test void runningDuplicateCannotClaimAgain() {
        assertThat(f.transactions.claimTaskExecution(f.message)).isNull();
        verify(f.tasks,never()).claimExecution(any(),anyLong(),anyInt(),anyInt());
    }

    @Test void mismatchAndMissingTaskCannotAcknowledge() {
        assertThatThrownBy(() -> f.transactions.claimTaskExecution(new ReportTaskMessage(3L,2L,2L))).isInstanceOf(IllegalStateException.class);
        when(f.tasks.getByIdForUpdate(3L)).thenReturn(null);
        assertThatThrownBy(() -> f.transactions.claimTaskExecution(f.message)).isInstanceOf(IllegalStateException.class);
    }

    @Test void databaseFailureMustRetry() {
        when(f.tasks.getByIdForUpdate(3L)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> f.transactions.claimTaskExecution(f.message)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test void queuedClaimCapturesOwnVersion() {
        queued(); when(f.sessions.claimReportExecution(2L,3)).thenReturn(1);
        when(f.tasks.claimExecution(3L,3L,4,600)).thenReturn(1);
        assertThat(f.transactions.claimTaskExecution(f.message).executionVersion()).isEqualTo(4);
        verify(f.sessions,times(1)).getReportVersionForUpdate(2L);
    }

    @Test void casFailureIsNotAcknowledged() {
        queued();
        assertThatThrownBy(() -> f.transactions.claimTaskExecution(f.message)).isInstanceOf(IllegalStateException.class);
    }

    @Test void exactQueueDeadlineRejectsClaimAndPersistsFailure() {
        queued(); f.task.setQueueDeadline(f.now);
        when(f.sessions.markReportFailed(2L,3)).thenReturn(1);
        when(f.tasks.finishFailure(3L,State.QUEUED,"QUEUE_EXPIRED")).thenReturn(1);
        assertThat(f.transactions.claimTaskExecution(f.message)).isNull();
        verify(f.sessions,never()).claimReportExecution(any(),anyInt());
    }

    @Test void scannerIgnoresExecutionBeforeDeadline() {
        f.transactions.expireTask(f.message);
        verify(f.tasks,never()).finishFailure(any(),any(),any());
    }

    @Test void sendLimitDoesNotFailConfirmedSent() {
        queued(); f.task.setSendAttempts(20); f.task.setDeliveryState(Delivery.SENT);
        f.transactions.expireTask(f.message);
        verify(f.tasks,never()).finishFailure(any(),any(),any());
    }

    @Test void lastUnknownSendFailsOnlyAfterLeaseEnds() {
        queued(); f.task.setSendAttempts(20); f.task.setDeliveryState(Delivery.LEASED); f.task.setDispatchLeaseUntil(f.now.plusSeconds(1));
        f.transactions.expireTask(f.message);
        verify(f.tasks,never()).finishFailure(any(),any(),any());
        f.task.setDispatchLeaseUntil(f.now);
        when(f.sessions.markReportFailed(2L,3)).thenReturn(1);
        when(f.tasks.finishFailure(3L,State.QUEUED,"SEND_EXHAUSTED")).thenReturn(1);
        f.transactions.expireTask(f.message);
        verify(f.tasks).finishFailure(3L,State.QUEUED,"SEND_EXHAUSTED");
    }

    @ParameterizedTest @ValueSource(strings = {"NOT_STARTED", "FAILED"})
    void requestCreatesFreshAttemptAfterCas(String status) {
        f.session.setReportStatus(InterviewReportStatus.valueOf(status));
        when(f.sessions.claimReportGeneration(2L,4,f.session.getReportStatus())).thenReturn(1);
        when(f.tasks.insertQueued(any(),eq(600))).thenAnswer(i -> { ((InterviewReportTask)i.getArgument(0)).setId(11L); return 1; });
        assertThat(f.transactions.acceptReportTask(1L,2L)).isEqualTo(InterviewReportStatus.GENERATING);
        var captor = org.mockito.ArgumentCaptor.forClass(InterviewReportTask.class);
        verify(f.tasks).insertQueued(captor.capture(),eq(600));
        assertThat(captor.getValue().getAttemptVersion()).isEqualTo(5);
        var order = inOrder(f.sessions,f.tasks);
        order.verify(f.sessions).getReportVersionForUpdate(2L);
        order.verify(f.sessions).claimReportGeneration(2L,4,InterviewReportStatus.valueOf(status));
        order.verify(f.tasks).insertQueued(any(),eq(600)); verifyNoInteractions(f.generator);
    }

    @Test void generatingPostNeverReclaimsHistoricalWork() {
        assertThat(f.transactions.acceptReportTask(1L,2L)).isEqualTo(InterviewReportStatus.GENERATING);
        verify(f.sessions,never()).claimReportGeneration(any(),any(),any());
        verify(f.tasks,never()).insertQueued(any(),anyInt());
    }

    @Test void disabledFeatureDoesNotRequireTaskTableMigration() {
        f.properties.setEnabled(false);
        assertThat(f.transactions.acceptReportTask(1L,2L)).isEqualTo(InterviewReportStatus.GENERATING);
        f.session.setReportStatus(InterviewReportStatus.NOT_STARTED);
        assertThatThrownBy(() -> f.transactions.acceptReportTask(1L,2L))
                .isInstanceOf(com.kun.aiinterview.common.exception.ServiceUnavailableException.class)
                .hasMessage("报告生成服务未启用，请联系管理员");
        verifyNoInteractions(f.tasks);
    }

    @ParameterizedTest @ValueSource(strings = {"NOT_STARTED", "FAILED", "READY", "GENERATING"})
    void disabledFeatureHasExplicitContractForEveryStatus(String status) {
        f.properties.setEnabled(false);
        f.session.setReportStatus(InterviewReportStatus.valueOf(status));
        if (status.equals("READY")) when(f.reports.getBySessionId(2L)).thenReturn(f.report);
        if (status.equals("NOT_STARTED") || status.equals("FAILED")) {
            assertThatThrownBy(() -> f.transactions.acceptReportTask(1L,2L))
                    .isInstanceOf(com.kun.aiinterview.common.exception.ServiceUnavailableException.class)
                    .hasMessage("报告生成服务未启用，请联系管理员");
        } else {
            assertThat(f.transactions.acceptReportTask(1L,2L)).isEqualTo(f.session.getReportStatus());
        }
        verifyNoInteractions(f.tasks, f.provider, f.generator, f.weaknesses, f.events);
        verify(f.sessions,never()).claimReportGeneration(any(),any(),any());
        verify(f.reports,never()).insertReport(any());
    }

    @ParameterizedTest @ValueSource(strings = {"paused", "generator"})
    void enabledButUnavailableIsNotDescribedAsDisabled(String reason) {
        f.session.setReportStatus(InterviewReportStatus.NOT_STARTED);
        if (reason.equals("paused")) f.properties.setAccepting(false);
        else when(f.provider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> f.transactions.acceptReportTask(1L,2L))
                .isInstanceOf(com.kun.aiinterview.common.exception.ServiceUnavailableException.class)
                .hasMessage("报告生成服务暂不受理新请求，请稍后重试");
        verify(f.tasks,never()).insertQueued(any(),anyInt());
        verify(f.sessions,never()).claimReportGeneration(any(),any(),any());
        verifyNoInteractions(f.generator, f.weaknesses, f.events);
    }

    @ParameterizedTest @ValueSource(strings = {"owner", "missing", "incomplete", "final", "disabled", "generator", "active", "cas", "insert", "key"})
    void acceptanceRejectsWithoutModelCalls(String fault) {
        f.session.setReportStatus(InterviewReportStatus.NOT_STARTED);
        switch (fault) {
            case "owner" -> f.session.setUserId(99L);
            case "missing" -> when(f.sessions.getReportVersionForUpdate(2L)).thenReturn(null);
            case "incomplete" -> f.session.setStatus(InterviewSessionStatus.IN_PROGRESS);
            case "final" -> f.evaluation.setEvaluationPhase(EvaluationPhase.INITIAL);
            case "disabled" -> f.properties.setEnabled(false);
            case "generator" -> when(f.provider.getIfAvailable()).thenReturn(null);
            case "active" -> when(f.tasks.listActiveBySessionId(2L)).thenReturn(List.of(f.task));
            case "insert", "key" -> {
                when(f.sessions.claimReportGeneration(2L,4,InterviewReportStatus.NOT_STARTED)).thenReturn(1);
                if (fault.equals("key")) when(f.tasks.insertQueued(any(),anyInt())).thenReturn(1);
            }
        }
        assertThatThrownBy(() -> f.transactions.acceptReportTask(1L,2L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(f.generator);
    }

    @ParameterizedTest @ValueSource(longs = {2147483645L,2147483647L,4294967295L})
    void unsignedVersionsFailWithoutIntegerOverflow(long version) {
        f.session.setReportStatus(InterviewReportStatus.NOT_STARTED);
        when(f.sessions.getReportVersionForUpdate(2L)).thenReturn(version);
        when(f.sessions.failReportVersionExhausted(2L,version)).thenReturn(1);
        assertThat(f.transactions.acceptReportTask(1L,2L)).isEqualTo(InterviewReportStatus.FAILED);
        verify(f.tasks,never()).insertQueued(any(),anyInt());
    }

    private void queued() { f.task.setTaskState(State.QUEUED); f.task.setExecutionVersion(null); f.session.setVersion(3); }
}
