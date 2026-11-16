package com.kun.aiinterview.interview.report.async;

import com.kun.aiinterview.interview.service.InterviewReportService;
import com.kun.aiinterview.interview.service.InterviewReportTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportMessageConsumerTest {
    InterviewReportTransactionService tx=mock(InterviewReportTransactionService.class);
    InterviewReportService reports=mock(InterviewReportService.class);
    ReportMessageConsumer consumer=new ReportMessageConsumer(tx,reports);
    ReportTaskMessage message=new ReportTaskMessage(1L,2L,3L);
    @Test void noClaimNoModel() { consumer.onMessage(message);verifyNoInteractions(reports); }
    @Test void callbackWaitsForCompletionAndPropagatesFailure() {
        var execution=new ReportExecution(message,4,null);when(tx.claimTaskExecution(message)).thenReturn(execution);
        doThrow(new DataAccessResourceFailureException("uncertain")).when(reports).generateClaimedReport(execution);
        assertThatThrownBy(() -> consumer.onMessage(message)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(reports).generateClaimedReport(execution);
    }
    @Test void unreadableDatabaseIsNotDuplicateAck() {
        when(tx.claimTaskExecution(message)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> consumer.onMessage(message)).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(reports);
    }
    @Test void invalidIdentifierCannotAcknowledgeOrReadDatabase() {
        for(var invalid:java.util.List.of(new ReportTaskMessage(null,2L,3L),new ReportTaskMessage(0L,2L,3L),
                new ReportTaskMessage(1L,-2L,3L),new ReportTaskMessage(1L,2L,4294967295L))) {
            assertThatThrownBy(() -> consumer.onMessage(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(tx,reports);
    }
}
