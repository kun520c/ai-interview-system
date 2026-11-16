package com.kun.aiinterview.interview.report.async;

import com.kun.aiinterview.interview.service.InterviewReportService;
import com.kun.aiinterview.interview.service.InterviewReportTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportMessageConsumer {
    private final InterviewReportTransactionService transactions;
    private final InterviewReportService reports;

    /** Runs synchronously on the MQ callback. Exceptions mean retry, never a local async handoff. */
    public void onMessage(ReportTaskMessage message) {
        message.validate();
        ReportExecution execution = transactions.claimTaskExecution(message);
        if (execution != null) reports.generateClaimedReport(execution);
    }
}
