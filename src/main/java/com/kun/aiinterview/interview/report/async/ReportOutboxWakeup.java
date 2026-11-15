package com.kun.aiinterview.interview.report.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;

/** A lossy latency hint. The committed Outbox and periodic scan remain the reliability boundary. */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "report.async", name = "enabled", havingValue = "true")
public class ReportOutboxWakeup {
    public record TaskAccepted(long taskId) {}

    private final ReportOutboxDispatcher dispatcher;
    private final TaskExecutor executor;
    private final ReportAsyncProperties properties;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ReportOutboxWakeup(ReportOutboxDispatcher dispatcher,
                             @Qualifier("reportWakeupExecutor") TaskExecutor executor,
                             ReportAsyncProperties properties) {
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAccepted(TaskAccepted event) {
        // Coalesce both queued and running hints. A hint arriving during dispatch may be lost;
        // the next periodic scan will pick it up. Never loop/drain indefinitely after a commit.
        if (!properties.isWakeupEnabled() || !scheduled.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    // A separate thread has its own MyBatis connections/transaction boundaries.
                    // Both scan and hint use the same dispatch lease CAS and settlement code.
                    dispatcher.dispatchDue();
                } catch (RuntimeException failure) {
                    log.warn("REPORT_WAKEUP_FAILED type={}", failure.getClass().getSimpleName());
                } finally {
                    scheduled.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            scheduled.set(false);
            // Acceptance already committed. A rejected hint must not turn it into HTTP 500.
            log.warn("REPORT_WAKEUP_REJECTED taskId={} type={}", event.taskId(), rejected.getClass().getSimpleName());
        }
    }
}
