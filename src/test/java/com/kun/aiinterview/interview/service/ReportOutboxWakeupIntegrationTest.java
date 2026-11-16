package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.report.async.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL commit/rollback and dispatch CAS; transport is a fake, no paid API. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ReportOutboxWakeupIntegrationTest.WakeupConfiguration.class)
class ReportOutboxWakeupIntegrationTest extends ReportDatabaseFixture {
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ReportOutboxDispatcher dispatcher;
    @MockitoBean(name = "reportWakeupExecutor") TaskExecutor executor;

    @TestConfiguration
    static class WakeupConfiguration {
        @Bean ReportOutboxWakeup wakeup(ReportOutboxDispatcher dispatcher,
                                       @org.springframework.beans.factory.annotation.Qualifier("reportWakeupExecutor") TaskExecutor executor,
                                       ReportAsyncProperties properties) {
            return new ReportOutboxWakeup(dispatcher, executor, properties);
        }
    }

    @BeforeEach void enableHints() { when(asyncProperties.isWakeupEnabled()).thenReturn(true); }

    @Test void committedTaskIsVisibleFromIndependentWakeupThreadAndDuplicatePostDoesNotNotifyAgain() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            var observed = new CompletableFuture<Integer>();
            doAnswer(i -> { worker.execute(i.getArgument(0)); return null; }).when(executor).execute(any());
            doAnswer(i -> {
                try {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    observed.complete(jdbc.queryForObject("SELECT COUNT(*) FROM interview_report_task WHERE session_id=?", Integer.class, sessionId));
                } catch (Throwable error) { observed.completeExceptionally(error); }
                return null;
            }).when(dispatcher).dispatchDue();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.generateReportForApi(userId, sessionId);
                verifyNoInteractions(executor, dispatcher);
            });
            assertThat(observed.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            service.generateReportForApi(userId, sessionId);
            verify(executor).execute(any());
        }
    }

    @Test void rollbackAfterEventPublicationNeitherWakesNorPersistsTask() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.generateReportForApi(userId, sessionId);
            status.setRollbackOnly();
        });
        verifyNoInteractions(executor, dispatcher);
        assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.NOT_STARTED);
    }

    @Test void rejectedHintKeepsSuccessfulAcceptanceAndPeriodicScanRecovers() throws Exception {
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        assertThat(service.generateReportForApi(userId, sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        var producer = mock(ReportMessageProducer.class);
        var scan = new ReportOutboxDispatcher(tasks, transactions, producer, new ReportAsyncProperties());
        scan.dispatchDue();
        verify(producer).send(any());
        assertThat(tasks.listActiveBySessionId(sessionId).getFirst().getDeliveryState())
                .isEqualTo(com.kun.aiinterview.interview.entity.InterviewReportTask.Delivery.SENT);
    }

    @Test void concurrentHintAndScanShareLeaseAndLateReceiptCannotRevertSuccess() throws Exception {
        // Drop the hint deterministically, then overlap two calls to the exact shared dispatch path.
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        service.generateReportForApi(userId, sessionId);
        var producer = mock(ReportMessageProducer.class);
        var scan = new ReportOutboxDispatcher(tasks, transactions, producer, new ReportAsyncProperties());
        var sending = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(i -> {
            sending.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            var message = (ReportTaskMessage) i.getArgument(0);
            service.generateClaimedReport(transactions.claimTaskExecution(message));
            return null;
        }).when(producer).send(any());
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<?> first = workers.submit(scan::dispatchDue);
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
            workers.submit(scan::dispatchDue).get(5, TimeUnit.SECONDS);
            release.countDown();first.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        verify(producer).send(any());
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.READY);
        assertThat(jdbc.queryForObject("SELECT task_state FROM interview_report_task WHERE session_id=?", String.class, sessionId)).isEqualTo("SUCCEEDED");
        assertThat(((Number) weakness().get("discovered_count")).intValue()).isEqualTo(1);
    }
}
