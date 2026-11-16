package com.kun.aiinterview.interview.report.async;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.mapper.InterviewReportTaskMapper;
import com.kun.aiinterview.interview.service.InterviewReportTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "report.async", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReportOutboxDispatcher {
    private final InterviewReportTaskMapper tasks;
    private final InterviewReportTransactionService transactions;
    private final ReportMessageProducer producer;
    private final ReportAsyncProperties properties;

    @Scheduled(fixedDelayString = "${report.async.scan-interval-ms:500}", scheduler = "reportScheduler")
    public void dispatchDue() {
        try {
            dispatch(tasks.listPending(properties.getScanBatchSize()));
            dispatch(tasks.listExpiredLeases(properties.getScanBatchSize()));
        } catch (RuntimeException unavailable) {
            log.error("REPORT_DISPATCH_SCAN_UNAVAILABLE type={}", unavailable.getClass().getSimpleName());
        }
    }

    private void dispatch(List<InterviewReportTask> candidates) {
        for (InterviewReportTask task : candidates) {
            try {
                ReportTaskMessage message = message(task);
                if (task.getSendAttempts() >= properties.getMaxSendAttempts()) {
                    transactions.expireTask(message);
                    continue;
                }
                String token = UUID.randomUUID().toString();
                int claimed = tasks.claimDispatch(task.getId(), token, properties.getDispatchLeaseSeconds(), properties.getMaxSendAttempts());
                if (claimed == 0) continue;
                if (claimed != 1) throw new IllegalStateException("Invalid dispatch claim count");
                try {
                    producer.send(message);
                } catch (Exception unknown) {
                    // A send timeout is not evidence that the Broker rejected the message.
                    int rows = tasks.rescheduleDispatch(task.getId(), token, properties.backoffSeconds(task.getSendAttempts() + 1));
                    if (rows != 0 && rows != 1) throw new IllegalStateException("Invalid reschedule count");
                    transactions.expireTask(message);
                    log.warn("REPORT_SEND_UNCERTAIN taskId={} attempt={} type={}", task.getId(), task.getSendAttempts() + 1,
                            unknown.getClass().getSimpleName());
                    continue;
                }
                // Do not convert DB settlement failure into send failure; leave the lease for recovery.
                int rows = tasks.markSent(task.getId(), token);
                if (rows != 0 && rows != 1) throw new IllegalStateException("Invalid sent count");
            } catch (RuntimeException failure) {
                log.error("REPORT_DISPATCH_RECOVERY_REQUIRED taskId={} type={}", task.getId(), failure.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "${report.async.scan-interval-ms:500}", scheduler = "reportScheduler")
    public void expireDue() {
        try {
            expire(tasks.listExpiredQueued(properties.getScanBatchSize()));
            expire(tasks.listExpiredRunning(properties.getScanBatchSize()));
        } catch (RuntimeException unavailable) {
            log.error("REPORT_EXPIRY_SCAN_UNAVAILABLE type={}", unavailable.getClass().getSimpleName());
        }
    }

    private void expire(List<InterviewReportTask> candidates) {
        for (InterviewReportTask task : candidates) {
            try {
                transactions.expireTask(message(task));
            } catch (RuntimeException failure) {
                log.error("REPORT_EXPIRY_RECOVERY_REQUIRED taskId={} type={}", task.getId(), failure.getClass().getSimpleName());
            }
        }
    }

    public static ReportTaskMessage message(InterviewReportTask task) {
        return new ReportTaskMessage(task.getId(), task.getSessionId(), task.getAttemptVersion());
    }
}
