package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.judge.JudgeConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class JevProxyTransportTest {
    private final JevRequest request = new JevRequest("jev-latest", "offline answer",
            Map.of("accuracy", new JevRequest.Question("score", "rate accuracy", List.of("poor", "good"))));

    @Test
    void disabledProxyPreservesDefaultSelectorAndRedirectPolicy() {
        var original = ProxySelector.getDefault();
        try (var client = JevClient.createHttpClient(new JevProperties())) {
            assertThat(client.proxy()).isEmpty();
            assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(ProxySelector.getDefault()).isSameAs(original);
        }
    }

    @Test
    void enabledSelectorAlwaysSelectsOnlyConfiguredHttpProxy() {
        var properties = properties(7897);
        try (var client = JevClient.createHttpClient(properties)) {
            var selector = client.proxy().orElseThrow();
            for (String uri : List.of("https://api.typesafe.ai/v1/systemone", "http://example.com/",
                    "https://example.com/", "http://localhost/", "http://127.0.0.1/")) {
                var selected = selector.select(URI.create(uri));
                assertThat(selected).hasSize(1);
                assertThat(selected.getFirst().type()).isEqualTo(Proxy.Type.HTTP);
                assertThat(selected.getFirst().address()).isEqualTo(new InetSocketAddress("127.0.0.1", 7897));
            }
            selector.connectFailed(URI.create("https://api.typesafe.ai"),
                    new InetSocketAddress("127.0.0.1", 7897), new java.io.IOException("offline"));
            assertThat(selector.select(URI.create("https://api.typesafe.ai")).getFirst().type())
                    .isEqualTo(Proxy.Type.HTTP);
        }
    }

    @Test
    void productionJevPostArrivesAtProxyAndNotAtOrigin() throws Exception {
        var originCalls = new AtomicInteger();
        var proxyCalls = new AtomicInteger();
        var proxyUri = new AtomicReference<String>();
        var origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin.createContext("/", exchange -> { originCalls.incrementAndGet(); exchange.close(); });
        proxy.createContext("/", exchange -> {
            proxyCalls.incrementAndGet();
            proxyUri.set(exchange.getRequestURI().toString());
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"model\":\"jev-offline\",\"answers\":{\"accuracy\":{\"type\":\"score\",\"score\":0.5}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        origin.start();
        proxy.start();
        try {
            var properties = properties(proxy.getAddress().getPort());
            properties.setBaseUrl(URI.create("http://127.0.0.1:" + origin.getAddress().getPort()));
            var client = new JudgeConfiguration().jevClient(RestClient.builder(), properties, new ObjectMapper());
            assertThat(client.evaluate(request).model()).isEqualTo("jev-offline");
            assertThat(proxyCalls).hasValue(1);
            assertThat(originCalls).hasValue(0);
            assertThat(proxyUri).hasValue(properties.getBaseUrl() + "/v1/systemone");
        } finally {
            proxy.stop(0);
            origin.stop(0);
        }
    }

    @Test
    void unavailableProxyCannotReachListeningOriginOrFallBackToDirect() throws Exception {
        var calls = new AtomicInteger();
        var origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin.createContext("/", exchange -> { calls.incrementAndGet(); exchange.close(); });
        origin.start();
        // A bound, non-listening socket reserves a port that cannot accept TCP connections.
        try (var unavailableProxy = new Socket()) {
            unavailableProxy.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = unavailableProxy.getLocalPort();
            var properties = properties(port);
            properties.setBaseUrl(URI.create("http://127.0.0.1:" + origin.getAddress().getPort()));
            var client = new JudgeConfiguration().jevClient(RestClient.builder(), properties, new ObjectMapper());
            assertThatThrownBy(() -> client.evaluate(request)).isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Jev HTTP或JSON解析失败").hasNoCause();
            assertThat(calls).hasValue(0);
        } finally {
            origin.stop(0);
        }
    }

    @Test
    void directConstructionAlsoRejectsMissingProxyConfiguration() {
        var properties = new JevProperties();
        properties.getProxy().setEnabled(true);
        assertThatThrownBy(() -> JevClient.createHttpClient(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    private JevProperties properties(int port) {
        var properties = new JevProperties();
        properties.setApiKey("offline-key");
        properties.setConnectTimeout(Duration.ofMillis(500));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.getProxy().setEnabled(true);
        properties.getProxy().setHost("127.0.0.1");
        properties.getProxy().setPort(port);
        return properties;
    }
}
