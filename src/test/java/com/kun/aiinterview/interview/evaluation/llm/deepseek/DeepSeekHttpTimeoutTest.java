package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Loopback only: exercises the production JDK factory, which MockRestServiceServer replaces. */
class DeepSeekHttpTimeoutTest {
    @ParameterizedTest
    @ValueSource(strings = {"headers", "body", "partial-json", "trickle"})
    @Timeout(10)
    void stalledResponseReleasesCallerWithinSingleAttemptBudget(String phase) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverThreads = Executors.newCachedThreadPool();
        var caller = Executors.newSingleThreadExecutor();
        var ticker = Executors.newSingleThreadScheduledExecutor();
        var received = new CountDownLatch(1);
        var releaseServer = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var requests = new AtomicInteger();
        server.setExecutor(serverThreads);
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                if (!phase.equals("headers")) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 0);
                    if (phase.equals("partial-json") || phase.equals("trickle")) {
                        exchange.getResponseBody().write("{\"model\":".getBytes(StandardCharsets.UTF_8));
                        exchange.getResponseBody().flush();
                    }
                    if (phase.equals("trickle")) {
                        ticker.scheduleAtFixedRate(() -> {
                            try {
                                exchange.getResponseBody().write(' ');
                                exchange.getResponseBody().flush();
                                writes.incrementAndGet();
                            } catch (IOException ignored) {
                                // Client timeout closes its response stream; no upstream payload is logged.
                            }
                        }, 0, 40, TimeUnit.MILLISECONDS);
                    }
                }
                received.countDown();
                releaseServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var properties = new DeepSeekProperties();
            properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.setApiKey("loopback-fake-key");
            properties.setModel("loopback-model");
            properties.setMaxTokens(4096);
            properties.setConnectTimeout(Duration.ofSeconds(1));
            properties.setReadTimeout(Duration.ofMillis(600));
            var restClient = new DeepSeekConfiguration().deepSeekRestClient(RestClient.builder(), properties);
            var client = new DeepSeekEvaluationClient(restClient, properties, new ObjectMapper());
            long start = System.nanoTime();
            var result = caller.submit(() -> catchThrowableOfType(
                    () -> client.completeJson("fake system", "fake answer"), ExternalServiceException.class));
            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(result.isDone()).as("synchronous caller still waits on upstream").isFalse();
            var failure = result.get(3, TimeUnit.SECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(failure).isNotNull().hasNoCause();
            assertThat(failure.getSuppressed()).isEmpty();
            // Body cancellation is an untyped IOException in Spring 6.2.19/JDK 21; do not guess by text.
            assertThat(failure.getMessage()).contains("category=" + (phase.equals("headers") ? "TIMEOUT" : "IO_ERROR"));
            assertThat(elapsedMs).isBetween(400L, 3000L);
            assertThat(requests).hasValue(1);
            assertThat(releaseServer.getCount()).as("caller finished before upstream was released").isEqualTo(1);
            if (phase.equals("trickle")) {
                assertThat(writes.get()).isGreaterThanOrEqualTo(3);
            }
            System.out.printf("B4C_LOOPBACK phase=%s readTimeoutMs=600 elapsedMs=%d requests=%d chunks=%d%n",
                    phase, elapsedMs, requests.get(), writes.get());
        } finally {
            releaseServer.countDown();
            ticker.shutdownNow();
            server.stop(0);
            serverThreads.shutdownNow();
            caller.shutdownNow();
            assertThat(ticker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(serverThreads.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(caller.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
