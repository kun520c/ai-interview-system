package com.kun.aiinterview.interview.evaluation.judge.jev;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

import static com.kun.aiinterview.interview.evaluation.judge.jev.JevStageException.Failure.*;

/** One monotonic budget per evaluation, including fallback. No shared executor or retry queue. */
final class JevRequestBudget {
    private final LongSupplier nanoTime;
    private final long started;
    private final long totalNanos;

    JevRequestBudget(Duration total) {
        this(total, System::nanoTime);
    }

    JevRequestBudget(Duration total, LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        started = nanoTime.getAsLong();
        totalNanos = total.toNanos();
    }

    Duration available(Duration reserve) {
        if (Thread.currentThread().isInterrupted()) {
            throw new JevStageException(INTERRUPTED);
        }
        return Duration.ofNanos(Math.max(0, totalNanos - (nanoTime.getAsLong() - started) - reserve.toNanos()));
    }

    long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
    }

    <T> T call(Duration timeout, Supplier<T> operation) {
        Duration remaining = available(Duration.ZERO);
        long nanos = Math.min(timeout.toNanos(), remaining.toNanos());
        if (nanos <= 0) {
            throw new JevStageException(REQUEST_BUDGET_EXHAUSTED);
        }
        // Cancellation interrupts Spring's JDK HTTP transport, which cancels its HTTP future.
        // Only this one operation runs here; a late result can never start another call.
        var task = new FutureTask<T>(operation::get);
        Thread.ofVirtual().name("jev-budget-call").start(task);
        try {
            T result = task.get(nanos, TimeUnit.NANOSECONDS);
            if (available(Duration.ZERO).isZero()) {
                throw new JevStageException(REQUEST_BUDGET_EXHAUSTED);
            }
            return result;
        } catch (TimeoutException exception) {
            throw new JevStageException(CALL_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JevStageException(INTERRUPTED);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Judge operation failed");
        } finally {
            task.cancel(true);
        }
    }
}
