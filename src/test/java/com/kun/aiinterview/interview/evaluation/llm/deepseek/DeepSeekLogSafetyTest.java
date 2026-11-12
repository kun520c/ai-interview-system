package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.common.exception.GlobalExceptionHandler;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import java.io.StringWriter;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeepSeekLogSafetyTest {
    private static final String SENTINEL = "B4B_TEST_SENSITIVE_BODY_20261112";
    private static final String API_KEY = "B4B_FAKE_API_KEY";
    private static final EvaluationPrompt PROMPT = new EvaluationPrompt(
            "test-v1", "B4B_FAKE_SYSTEM_PROMPT", "B4B_FAKE_USER_ANSWER");
    private static final String BODY = SENTINEL + "\r\nERROR FORGED_LOG_RECORD\u001b[31m\u0000"
            + " Authorization: Bearer " + API_KEY + " " + PROMPT.systemPrompt() + " " + PROMPT.userPrompt();

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final PatternLayout layout = new PatternLayout();
    private MockRestServiceServer server;
    private DeepSeekEvaluationClient client;
    private MockMvc mvc;
    private ProbeController controller;

    @BeforeEach
    void setUp() {
        var properties = new DeepSeekProperties();
        properties.setApiKey(API_KEY);
        properties.setModel("test-model");
        properties.setMaxTokens(2048);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setBaseUrl(URI.create("http://localhost"));
        var builder = new DeepSeekConfiguration().deepSeekRestClient(RestClient.builder(), properties).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DeepSeekEvaluationClient(builder.build(), properties, new ObjectMapper());
        controller = new ProbeController(() -> client.evaluate(PROMPT));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        layout.setContext(logger.getLoggerContext());
        layout.setPattern("%level %logger - %msg%n%ex{full}");
        layout.start();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        layout.stop();
        if (server != null) {
            server.verify();
        }
    }

    @ParameterizedTest
    @MethodSource("httpFailures")
    void shouldKeepHttpFailureOutOfExceptionAndRenderedLog(int httpStatus, boolean echoInput) throws Exception {
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andRespond(withStatus(HttpStatusCode.valueOf(httpStatus))
                        .header("X-Upstream-Diagnostic", BODY)
                        .contentType(MediaType.APPLICATION_JSON).body(echoInput ? BODY : "unavailable"));
        assertSafeFailure("category=HTTP_STATUS, httpStatus=" + httpStatus);
    }

    static Stream<Arguments> httpFailures() {
        return Stream.of(400, 401, 402, 429, 500, 503)
                .flatMap(status -> Stream.of(Arguments.of(status, false), Arguments.of(status, true)));
    }

    @Test
    void shouldRejectHttpErrorWithoutReadingBodyOrStatusText() throws Exception {
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(request -> new MockClientHttpResponse(new InputStream() {
                    @Override public int read() {
                        throw new AssertionError("error body must not be read");
                    }
                }, HttpStatusCode.valueOf(503)) {
                    @Override public String getStatusText() {
                        throw new AssertionError("untrusted status text must not be read");
                    }
                });
        assertSafeFailure("category=HTTP_STATUS, httpStatus=503");
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 599})
    void shouldRejectOtherNonSuccessStatuses(int httpStatus) throws Exception {
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withStatus(HttpStatusCode.valueOf(httpStatus)).body(BODY));
        assertSafeFailure("httpStatus=" + httpStatus);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkFailures")
    void shouldKeepOnlyControlledNetworkCategory(String category, IOException failure) throws Exception {
        failure.addSuppressed(new IOException(BODY));
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(withException(failure));
        assertSafeFailure("category=" + category);
    }

    static Stream<Arguments> networkFailures() {
        return Stream.of(
                Arguments.of("CONNECTION_ERROR", new ConnectException(BODY)),
                Arguments.of("CONNECTION_ERROR", new UnknownHostException(BODY)),
                Arguments.of("CONNECT_TIMEOUT", new HttpConnectTimeoutException(BODY)),
                Arguments.of("TIMEOUT", new HttpTimeoutException(BODY)),
                Arguments.of("TIMEOUT", new SocketTimeoutException(BODY)),
                Arguments.of("IO_ERROR", new IOException(BODY)),
                Arguments.of("TIMEOUT", new IOException(BODY, new HttpTimeoutException(BODY))));
    }

    @Test
    void shouldSanitizeTimeoutWhileReadingResponseBody() throws Exception {
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(request -> {
                    var response = new MockClientHttpResponse(new InputStream() {
                        @Override public int read() throws IOException { throw new SocketTimeoutException(BODY); }
                    }, HttpStatusCode.valueOf(200));
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    return response;
                });
        assertSafeFailure("category=TIMEOUT");
    }

    @Test
    void shouldSanitizeUnknownClientFailureIncludingSuppressedErrors() throws Exception {
        var failure = new RestClientException(BODY, new IOException(BODY));
        failure.addSuppressed(new ResourceAccessException(BODY));
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(request -> { throw failure; });
        assertSafeFailure("category=IO_ERROR");
    }

    @Test
    void shouldNotHideUnrelatedProgrammingErrors() {
        var failure = new IllegalStateException("controlled programming failure");
        server.expect(requestTo("http://localhost/chat/completions"))
                .andRespond(request -> { throw failure; });
        assertThatThrownBy(() -> client.evaluate(PROMPT)).isSameAs(failure);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void shouldRejectInvalidOutputWithoutLeakingIt(String scenario, ResponseCreator response, String diagnostic)
            throws Exception {
        server.expect(requestTo("http://localhost/chat/completions")).andRespond(response);
        assertSafeFailure(diagnostic);
    }

    static Stream<Arguments> invalidResponses() throws Exception {
        return Stream.of(
                Arguments.of("outer invalid JSON", withSuccess("{" + SENTINEL, MediaType.APPLICATION_JSON), "category=RESPONSE_JSON"),
                Arguments.of("outer type mismatch", withSuccess("{\"choices\":\"" + SENTINEL + "\"}", MediaType.APPLICATION_JSON), "category=RESPONSE_JSON"),
                Arguments.of("unknown content type", withSuccess(BODY, MediaType.TEXT_PLAIN), "category=HTTP_CLIENT_ERROR"),
                Arguments.of("empty", withSuccess("", MediaType.APPLICATION_JSON), "空响应"),
                Arguments.of("inner invalid JSON", completion("{" + SENTINEL, "stop"), "stage=evaluation_parse"),
                Arguments.of("inner type mismatch", completion("{\"correctnessScore\":\"" + SENTINEL + "\"}", "stop"), "stage=evaluation_parse"),
                Arguments.of("inner null", completion("null", "stop"), "解析结果不能为空"),
                Arguments.of("blank", completion(" ", "stop"), "缺少评价内容"),
                Arguments.of("truncated", completion("{}", "length"), "finishReason=length"),
                Arguments.of("filtered", completion("{}", "content_filter"), "finishReason=content_filter"),
                Arguments.of("unknown finish reason", completion("{}", BODY), "finishReason=UNKNOWN"));
    }

    private static ResponseCreator completion(String content, String finish) throws Exception {
        return withSuccess(new ObjectMapper().writeValueAsString(Map.of("model", "test-model",
                "choices", List.of(Map.of("index", 0, "finish_reason", finish,
                        "message", Map.of("role", "assistant", "content", content))))), MediaType.APPLICATION_JSON);
    }

    private void assertSafeFailure(String diagnostic) throws Exception {
        String response = mvc.perform(post("/probe/deepseek"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("外部服务暂时不可用"))
                .andReturn().getResponse().getContentAsString();
        assertThat(controller.failure).isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining(diagnostic).hasNoCause();
        assertThat(controller.failure.getSuppressed()).isEmpty();
        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("外部服务异常");
        StringWriter stack = new StringWriter();
        controller.failure.printStackTrace(new PrintWriter(stack));
        String proxy = ThrowableProxyUtil.asString(event.getThrowableProxy());
        String rendered = layout.doLayout(event);
        assertThat(rendered).contains("ERROR", "ExternalServiceException", diagnostic, "DeepSeekEvaluationClient");
        for (String output : List.of(response, controller.failure.toString(), stack.toString(), proxy, rendered)) {
            assertThat(output).doesNotContain(SENTINEL, API_KEY, "Authorization", "Bearer ",
                    PROMPT.systemPrompt(), PROMPT.userPrompt(), "FORGED_LOG_RECORD", "\r", "\u001b", "\u0000");
        }
    }

    @RestController
    static class ProbeController {
        private final Supplier<?> action;
        private RuntimeException failure;

        ProbeController(Supplier<?> action) {
            this.action = action;
        }

        @PostMapping("/probe/deepseek")
        Object invoke() {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                failure = exception;
                throw exception;
            }
        }
    }
}
