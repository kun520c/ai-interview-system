package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.shaded.io.grpc.Status;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.exception.ErrorCode;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.utils.RpcUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterizes the actual 2.6.20 JAR, including bugs; revisit on an SDK upgrade. */
class MilvusSdkRetryBehaviorTest {
    @Test
    void defaultConfigurationMeansSeventyFiveTotalCallableAttempts() {
        RetryConfig defaults = RetryConfig.builder().build();
        assertThat(defaults.getMaxRetryTimes()).isEqualTo(75);
        assertThat(defaults.getInitialBackOffMs()).isEqualTo(10);
        assertThat(defaults.getMaxBackOffMs()).isEqualTo(3000);
        assertThat(defaults.getBackOffMultiplier()).isEqualTo(3);
        assertThat(defaults.getMaxRetryTimeoutMs()).isZero();
        assertThat(defaults.isRetryOnRateLimit()).isTrue();
        // Only remove sleeps to exercise all 75 attempts without a multi-minute test.
        defaults.setInitialBackOffMs(0);
        RpcUtils rpc = rpc(defaults);
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();
        Object result = rpc.retry(() -> {
            attempts.incrementAndGet();
            throw Status.UNAVAILABLE.asRuntimeException();
        });
        record("default-exhaustion-zero-backoff", attempts, start);
        assertThat(attempts).hasValue(75);
        assertThat(result).isNull(); // 2.6.20 swallows its exhaustion exception.
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transientTransportOrServerRateLimitCanSucceedOnThirdAttempt(boolean rateLimit) {
        RpcUtils rpc = rpc(shortRetry(4));
        AtomicInteger attempts = new AtomicInteger();
        Object expected = new Object();
        long start = System.nanoTime();
        Object result = rpc.retry(() -> {
            if (attempts.incrementAndGet() < 3) {
                if (rateLimit) throw rateLimit();
                throw Status.UNAVAILABLE.asRuntimeException();
            }
            return expected;
        });
        record("retry-success-rateLimit=" + rateLimit, attempts, start);
        assertThat(result).isSameAs(expected);
        assertThat(attempts).hasValue(3);
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"DEADLINE_EXCEEDED", "PERMISSION_DENIED",
            "UNAUTHENTICATED", "INVALID_ARGUMENT", "ALREADY_EXISTS", "RESOURCE_EXHAUSTED", "UNIMPLEMENTED"})
    void nonRetryableGrpcStatusesFailImmediately(Status.Code code) {
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();
        assertThatThrownBy(() -> rpc(shortRetry(4)).retry(() -> {
            attempts.incrementAndGet();
            throw code.toStatus().asRuntimeException();
        })).isInstanceOf(MilvusClientException.class);
        record(code.name(), attempts, start);
        assertThat(attempts).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutBudgetIsSwallowedAndLateSuccessIsAccepted(boolean rateLimit) {
        RetryConfig config = shortRetry(4);
        config.setMaxRetryTimeoutMs(1);
        config.setInitialBackOffMs(15);
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();
        String result = rpc(config).retry(() -> {
            if (attempts.incrementAndGet() < 3) {
                if (rateLimit) throw rateLimit();
                throw Status.UNAVAILABLE.asRuntimeException();
            }
            return "late success";
        });
        record("ignored-budget-rateLimit=" + rateLimit, attempts, start);
        assertThat(result).isEqualTo("late success");
        assertThat(attempts).hasValue(3);
        assertThat(System.nanoTime() - start).isGreaterThan(1_000_000L);
    }

    @Test
    void retrySleepSwallowsInterruptAndContinues() {
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();
        try {
            String result = rpc(shortRetry(4)).retry(() -> {
                if (attempts.incrementAndGet() == 1) {
                    Thread.currentThread().interrupt();
                    throw Status.CANCELLED.asRuntimeException();
                }
                return "continued";
            });
            record("swallowed-interrupt", attempts, start);
            assertThat(result).isEqualTo("continued");
            assertThat(attempts).hasValue(2);
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void singleAttemptPreservesGrpcFailureAndDoesNotEnterDefectiveLoop() {
        AtomicInteger attempts = new AtomicInteger();
        var failure = Status.UNAVAILABLE.asRuntimeException();
        long start = System.nanoTime();
        assertThatThrownBy(() -> rpc(shortRetry(1)).retry(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isInstanceOf(MilvusClientException.class).hasCause(failure);
        record("single-attempt", attempts, start);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void nonRateLimitServerFailureAndUnexpectedCallableFailureAreNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        MilvusClientException serverFailure = new MilvusClientException(ErrorCode.SERVER_ERROR, "synthetic", 99, 1);
        assertThatThrownBy(() -> rpc(shortRetry(4)).retry(() -> {
            attempts.incrementAndGet();
            throw serverFailure;
        })).isSameAs(serverFailure);
        assertThat(attempts).hasValue(1);
        attempts.set(0);
        assertThatThrownBy(() -> rpc(shortRetry(4)).retry(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("synthetic malformed response");
        })).isInstanceOf(MilvusClientException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(1);
    }

    private static MilvusClientException rateLimit() {
        return new MilvusClientException(ErrorCode.SERVER_ERROR, "synthetic rate limit", 8, 49);
    }

    private static RetryConfig shortRetry(int attempts) {
        return RetryConfig.builder().maxRetryTimes(attempts).initialBackOffMs(1)
                .maxBackOffMs(15).backOffMultiplier(1).build();
    }

    private static RpcUtils rpc(RetryConfig config) {
        RpcUtils rpc = new RpcUtils();
        rpc.retryConfig(config);
        return rpc;
    }

    private static void record(String scenario, AtomicInteger attempts, long start) {
        System.out.printf("B4D SDK scenario=%s attempts=%d elapsedMs=%d%n",
                scenario, attempts.get(), (System.nanoTime() - start) / 1_000_000);
    }
}
