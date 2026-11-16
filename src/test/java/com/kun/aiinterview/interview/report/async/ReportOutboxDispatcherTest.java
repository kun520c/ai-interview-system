package com.kun.aiinterview.interview.report.async;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.mapper.InterviewReportTaskMapper;
import com.kun.aiinterview.interview.service.InterviewReportTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class ReportOutboxDispatcherTest {
    InterviewReportTaskMapper tasks=mock(InterviewReportTaskMapper.class);
    InterviewReportTransactionService tx=mock(InterviewReportTransactionService.class);
    ReportMessageProducer producer=mock(ReportMessageProducer.class);
    ReportAsyncProperties properties=new ReportAsyncProperties();
    ReportOutboxDispatcher dispatcher=new ReportOutboxDispatcher(tasks,tx,producer,properties);
    InterviewReportTask task=new InterviewReportTask();
    @BeforeEach void setup() {
        task.setId(1L);task.setSessionId(2L);task.setAttemptVersion(3L);
        when(tasks.listPending(16)).thenReturn(List.of(task));
    }
    @Test void onlyLeaseWinnerSends() throws Exception {
        dispatcher.dispatchDue(); verifyNoInteractions(producer);
        when(tasks.claimDispatch(eq(1L),anyString(),eq(30),eq(20))).thenReturn(1);
        dispatcher.dispatchDue(); verify(producer).send(new ReportTaskMessage(1L,2L,3L));
        verify(tasks).markSent(eq(1L),anyString());
    }
    @Test void uncertainSendUsesSameLeaseAndBoundedBackoff() throws Exception {
        when(tasks.claimDispatch(eq(1L),anyString(),eq(30),eq(20))).thenReturn(1);
        doThrow(new java.net.SocketTimeoutException()).when(producer).send(any());
        dispatcher.dispatchDue();
        var claim=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tasks).claimDispatch(eq(1L),claim.capture(),eq(30),eq(20));
        verify(tasks).rescheduleDispatch(1L,claim.getValue(),2);
        verify(tasks,never()).markSent(any(),any()); verify(tx).expireTask(any());
    }
    @Test void dbSettlementFailureDoesNotPretendSendFailedOrAcknowledge() throws Exception {
        when(tasks.claimDispatch(eq(1L),anyString(),eq(30),eq(20))).thenReturn(1);
        when(tasks.markSent(eq(1L),anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        dispatcher.dispatchDue(); verify(producer).send(any());
        verify(tasks,never()).rescheduleDispatch(any(),any(),anyInt());
    }
    @Test void finalAttemptRecoveredWithoutAnotherSend() {
        task.setSendAttempts(20);dispatcher.dispatchDue();
        verify(tx).expireTask(new ReportTaskMessage(1L,2L,3L));verifyNoInteractions(producer);
    }
    @Test void failingRowDoesNotStarveOtherRows() throws Exception {
        var other=new InterviewReportTask();other.setId(4L);other.setSessionId(5L);other.setAttemptVersion(6L);
        when(tasks.listPending(16)).thenReturn(List.of(task,other));
        when(tasks.claimDispatch(eq(1L),anyString(),anyInt(),anyInt())).thenThrow(new DataAccessResourceFailureException("offline"));
        when(tasks.claimDispatch(eq(4L),anyString(),anyInt(),anyInt())).thenReturn(1);
        dispatcher.dispatchDue(); verify(producer).send(new ReportTaskMessage(4L,5L,6L));
    }
    @Test void databaseOutageLeavesRecoveryForNextSweep() {
        when(tasks.listExpiredQueued(16)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatCode(dispatcher::expireDue).doesNotThrowAnyException();verifyNoInteractions(tx);
        doReturn(List.of(task)).when(tasks).listExpiredQueued(16);
        dispatcher.expireDue(); verify(tx).expireTask(any());
    }
    @Test void backoffCapsAtSixtySeconds() {
        assertThat(java.util.stream.IntStream.rangeClosed(1,8).map(properties::backoffSeconds).toArray())
                .containsExactly(2,4,8,16,32,60,60,60);
    }
}
