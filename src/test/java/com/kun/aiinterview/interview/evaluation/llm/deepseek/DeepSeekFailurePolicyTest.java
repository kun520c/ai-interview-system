package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** One business attempt must never turn an upstream failure into another billable HTTP call. */
class DeepSeekFailurePolicyTest {
    private static final String PRIVATE = "B4C_PRIVATE_UPSTREAM_DATA";
    private static final EvaluationPrompt PROMPT = new EvaluationPrompt("test", "fake system", "fake answer");
    private MockRestServiceServer server;
    private DeepSeekEvaluationClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Thread> requestThread = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://deepseek.invalid");
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new DeepSeekProperties();
        properties.setModel("test-model");
        properties.setMaxTokens(4096);
        client = new DeepSeekEvaluationClient(builder.build(), properties, new ObjectMapper());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400,INVALID_REQUEST", "401,AUTHENTICATION", "402,BALANCE", "403,PERMISSION",
            "408,UPSTREAM_TIMEOUT", "422,INVALID_REQUEST", "429,RATE_LIMIT",
            "500,UPSTREAM_SERVER", "502,UPSTREAM_GATEWAY", "503,UPSTREAM_UNAVAILABLE",
            "504,UPSTREAM_TIMEOUT", "302,UNEXPECTED_STATUS", "404,UNEXPECTED_STATUS", "599,UNEXPECTED_STATUS"
    })
    void httpStatusHasSafeReasonAndExactlyOneAttempt(int status, String reason) {
        expect(withStatus(HttpStatusCode.valueOf(status)).body(PRIVATE));
        assertFailure("category=HTTP_STATUS", "httpStatus=" + status, "reason=" + reason);
    }

    @ParameterizedTest
    @MethodSource("networkFailures")
    void transportFailureIsClassifiedByTypeWithoutRetry(String category, IOException failure) {
        failure.addSuppressed(new IOException(PRIVATE));
        expect(withException(failure));
        assertFailure("category=" + category);
    }

    static Stream<Arguments> networkFailures() {
        var wrappedConnectTimeout = new HttpTimeoutException(PRIVATE);
        wrappedConnectTimeout.initCause(new HttpConnectTimeoutException(PRIVATE));
        return Stream.of(
                Arguments.of("CONNECTION_ERROR", new ConnectException(PRIVATE)),
                Arguments.of("CONNECTION_ERROR", new UnknownHostException(PRIVATE)),
                Arguments.of("CONNECT_TIMEOUT", new HttpConnectTimeoutException(PRIVATE)),
                Arguments.of("TIMEOUT", new HttpTimeoutException(PRIVATE)),
                Arguments.of("TIMEOUT", new SocketTimeoutException(PRIVATE)),
                Arguments.of("IO_ERROR", new IOException(PRIVATE)),
                // Jackson can wrap a transport failure while consuming the response stream.
                Arguments.of("TIMEOUT", new JsonParseException(null, PRIVATE, new SocketTimeoutException(PRIVATE))),
                Arguments.of("CONNECT_TIMEOUT", wrappedConnectTimeout)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "86400", "Wed, 21 Oct 2099 07:28:00 GMT",
            "Wed, 21 Oct 2015 07:28:00 GMT", "-1", "nonsense", "999999999999999999999999"})
    void retryAfterDoesNotIntroduceWaitingOrAnotherAttempt(String retryAfter) {
        expect(withStatus(HttpStatusCode.valueOf(429)).header("Retry-After", retryAfter).body(PRIVATE));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),
                () -> assertFailure("reason=RATE_LIMIT"));
    }

    @ParameterizedTest
    @MethodSource("contentFailures")
    void invalidContentFailsOnceWithoutRetry(String scenario, ResponseCreator response, String diagnostic) {
        expect(response);
        assertFailure(diagnostic);
    }

    static Stream<Arguments> contentFailures() throws Exception {
        return Stream.of(
                Arguments.of("empty 200", withSuccess("", MediaType.APPLICATION_JSON), "空响应"),
                Arguments.of("empty 204", withNoContent(), "空响应"),
                Arguments.of("JSON null", withSuccess("null", MediaType.APPLICATION_JSON), "空响应"),
                Arguments.of("outer malformed", withSuccess("{" + PRIVATE, MediaType.APPLICATION_JSON), "RESPONSE_JSON"),
                Arguments.of("incomplete envelope", withSuccess("{}", MediaType.APPLICATION_JSON), "缺少模型名称"),
                Arguments.of("inner malformed", completion("{" + PRIVATE, "stop"), "stage=evaluation_parse"),
                Arguments.of("length", completion("{}", "length"), "finishReason=length"),
                Arguments.of("filter", completion("{}", "content_filter"), "finishReason=content_filter"),
                Arguments.of("resource", completion("{}", "insufficient_system_resource"), "finishReason=insufficient_system_resource"),
                Arguments.of("tools", completion("{}", "tool_calls"), "finishReason=tool_calls"),
                Arguments.of("unknown", completion("{}", PRIVATE), "finishReason=UNKNOWN")
        );
    }

    @Test
    void explicitSecondInvocationCanRecoverAndRunsOnCallingThread() throws Exception {
        expect(withStatus(HttpStatusCode.valueOf(503)).body(PRIVATE));
        expect(completion("{}", "stop"));
        assertFailure("reason=UPSTREAM_UNAVAILABLE");
        assertThat(client.completeJson(PROMPT.systemPrompt(), PROMPT.userPrompt()).rawJson()).isEqualTo("{}");
        assertThat(calls).hasValue(2);
        assertThat(requestThread.get()).isSameAs(Thread.currentThread());
    }

    private void expect(ResponseCreator response) {
        server.expect(requestTo("http://deepseek.invalid/chat/completions"))
                .andRespond(request -> {
                    requestThread.set(Thread.currentThread());
                    calls.incrementAndGet();
                    return response.createResponse(request);
                });
    }

    private void assertFailure(String... diagnostics) {
        var failure = catchThrowableOfType(() -> client.evaluate(PROMPT), ExternalServiceException.class);
        assertThat(failure).isNotNull().hasMessageContainingAll(diagnostics).hasNoCause();
        assertThat(failure.getMessage()).doesNotContain(PRIVATE);
        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(calls).hasValue(1);
    }

    private static ResponseCreator completion(String content, String finish) throws Exception {
        return withSuccess(new ObjectMapper().writeValueAsString(Map.of("model", "test-model",
                "choices", List.of(Map.of("index", 0, "finish_reason", finish,
                        "message", Map.of("role", "assistant", "content", content))))), MediaType.APPLICATION_JSON);
    }
}
