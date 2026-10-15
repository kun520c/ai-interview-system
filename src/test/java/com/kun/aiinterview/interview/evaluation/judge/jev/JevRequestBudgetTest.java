package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class JevRequestBudgetTest {
    @Test
    void nativeFeedbackRetryAndFallbackShareOneMonotonicBudget() {
        var time = new AtomicLong();
        var budget = new JevRequestBudget(Duration.ofSeconds(80), time::get);
        budget.call(Duration.ofSeconds(20), () -> time.addAndGet(Duration.ofSeconds(20).toNanos()));
        assertThat(budget.available(Duration.ofSeconds(25))).isEqualTo(Duration.ofSeconds(35));
        budget.call(Duration.ofSeconds(20), () -> time.addAndGet(Duration.ofSeconds(20).toNanos()));
        assertThat(budget.available(Duration.ofSeconds(25))).isEqualTo(Duration.ofSeconds(15));
        budget.call(Duration.ofSeconds(15), () -> time.addAndGet(Duration.ofSeconds(15).toNanos()));
        assertThat(budget.available(Duration.ZERO)).isEqualTo(Duration.ofSeconds(25));
        budget.call(Duration.ofSeconds(25), () -> time.addAndGet(Duration.ofSeconds(24).toNanos()));
        assertThat(budget.elapsedMillis()).isEqualTo(79_000);
        assertThat(budget.available(Duration.ZERO)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void elapsedBudgetDoesNotDispatchAnotherOperationOrAcceptLateResults() {
        var time = new AtomicLong();
        var budget = new JevRequestBudget(Duration.ofSeconds(5), time::get);
        assertThatThrownBy(() -> budget.call(Duration.ofSeconds(20),
                () -> time.addAndGet(Duration.ofSeconds(6).toNanos())))
                .isInstanceOf(JevStageException.class).hasMessageContaining("REQUEST_BUDGET_EXHAUSTED");
        var called = new AtomicBoolean();
        assertThatThrownBy(() -> budget.call(Duration.ofSeconds(20), () -> called.getAndSet(true)))
                .isInstanceOf(JevStageException.class).hasMessageContaining("REQUEST_BUDGET_EXHAUSTED");
        assertThat(called).isFalse();
    }

    @Test
    void realJdkTransportReadingPartialBodyIsCancelledAtBudgetWithoutAnyRemoteModel() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var clientExited = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            var properties = new JevProperties();
            var factory = new JdkClientHttpRequestFactory(JevClient.createHttpClient(properties));
            factory.setReadTimeout(Duration.ofSeconds(60));
            var client = new JevClient(RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .requestFactory(factory).build(), new ObjectMapper());
            var question = new JevRequest.Question("choice", "offline", java.util.Map.of("yes", "yes"));
            var request = new JevRequest("offline", "offline data", java.util.Map.of("q", question));
            var budget = new JevRequestBudget(Duration.ofSeconds(2));
            assertThatThrownBy(() -> budget.call(Duration.ofSeconds(60), () -> {
                try { return client.evaluate(request); }
                finally { clientExited.countDown(); }
            })).isInstanceOf(JevStageException.class).hasMessageContaining("CALL_TIMEOUT");
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(clientExited.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(budget.elapsedMillis()).isLessThan(5_000);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
