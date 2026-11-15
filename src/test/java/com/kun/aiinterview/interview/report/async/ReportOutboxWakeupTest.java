package com.kun.aiinterview.interview.report.async;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportOutboxWakeupTest {
    final ReportOutboxDispatcher dispatcher = mock(ReportOutboxDispatcher.class);
    final ReportAsyncProperties properties = new ReportAsyncProperties();
    final ReportOutboxWakeup.TaskAccepted event = new ReportOutboxWakeup.TaskAccepted(1);

    @Test void coalescesPendingAndRunningHintsIntoOneBoundedScan() {
        List<Runnable> queue = new ArrayList<>();
        var wakeup = new ReportOutboxWakeup(dispatcher, queue::add, properties);
        for (int i = 0; i < 1000; i++) wakeup.onTaskAccepted(event);
        assertThat(queue).hasSize(1);
        doAnswer(invocation -> { wakeup.onTaskAccepted(event); return null; }).when(dispatcher).dispatchDue();
        queue.removeFirst().run();
        assertThat(queue).isEmpty();
        verify(dispatcher).dispatchDue();
        wakeup.onTaskAccepted(event);
        assertThat(queue).hasSize(1);
    }

    @Test void rejectionDoesNotEscapeCommitAndAllowsLaterHint() {
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        var wakeup = new ReportOutboxWakeup(dispatcher, executor, properties);
        assertThatCode(() -> wakeup.onTaskAccepted(event)).doesNotThrowAnyException();
        wakeup.onTaskAccepted(event);
        verify(executor, times(2)).execute(any());
        verifyNoInteractions(dispatcher);
    }

    @Test void failedScanDoesNotPermanentlySuppressHints() {
        List<Runnable> queue = new ArrayList<>();
        var wakeup = new ReportOutboxWakeup(dispatcher, queue::add, properties);
        doThrow(new IllegalStateException()).doNothing().when(dispatcher).dispatchDue();
        wakeup.onTaskAccepted(event);
        assertThatCode(() -> queue.removeFirst().run()).doesNotThrowAnyException();
        wakeup.onTaskAccepted(event);
        queue.removeFirst().run();
        verify(dispatcher, times(2)).dispatchDue();
    }

    @Test void switchDisablesHintWithoutDispatchingOrQueuing() {
        properties.setWakeupEnabled(false);
        TaskExecutor executor = mock(TaskExecutor.class);
        new ReportOutboxWakeup(dispatcher, executor, properties).onTaskAccepted(event);
        verifyNoInteractions(executor, dispatcher);
    }

    @Test void boundedExecutorNeverRunsRejectedWorkOnCallerThread() throws Exception {
        var executor = new ReportAsyncConfiguration.Scheduling().reportWakeupExecutor();
        executor.initialize();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try {
            executor.execute(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {}); // The sole queue slot is occupied.
            var wakeup = new ReportOutboxWakeup(dispatcher, executor, properties);
            org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(1),
                    () -> wakeup.onTaskAccepted(event));
            verifyNoInteractions(dispatcher);
            assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(1);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
