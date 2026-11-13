package com.kun.aiinterview.knowledge.embedding.springai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.GlobalExceptionHandler;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpringAiEmbeddingLogSafetyTest {
    private static final String SENTINEL = "B5_P2B_SENSITIVE_RESPONSE_SENTINEL";
    private static final String API_KEY = "B5_P2B_FAKE_API_KEY";
    private static final String INPUT = "B5_P2B_PRIVATE_EMBEDDING_INPUT";
    private static final String MODEL = "test-embedding-model";
    private static final String URL = "http://localhost:18080/v1/embeddings";
    private static final String BODY = SENTINEL + "\r\nERROR FORGED_LOG_RECORD\u001b[31m\u0000"
            + " Authorization: Bearer " + API_KEY + " " + INPUT;

    // Capture framework logs as well: OpenAiEmbeddingModel can log before the project adapter returns.
    private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final PatternLayout layout = new PatternLayout();
    private MockRestServiceServer server;
    private SpringAiEmbeddingClient client;
    private MockMvc mvc;
    private ProbeController controller;

    @BeforeEach
    void setUp() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(URI.create("http://localhost:18080/v1"));
        properties.setApiKey(API_KEY);
        properties.setModel(MODEL);
        properties.setDimension(3);
        properties.setBatchSize(20);
        properties.setProfileVersion("test-v1");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        var builder = EmbeddingConfiguration.createEmbeddingRestClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        var api = EmbeddingConfiguration.createOpenAiApi(properties, builder);
        client = new SpringAiEmbeddingClient(EmbeddingConfiguration.createEmbeddingModel(api, properties), properties);
        controller = new ProbeController(() -> client.embed(List.of(INPUT)));
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
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void shouldKeepHttpBodyOutOfLogs(int httpStatus) throws Exception {
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andRespond(withStatus(HttpStatusCode.valueOf(httpStatus))
                        .header("X-Upstream-Diagnostic", BODY)
                        .contentType(MediaType.APPLICATION_JSON).body(BODY));
        assertSafeFailure("category=HTTP_STATUS, httpStatus=" + httpStatus);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkFailures")
    void shouldClassifyNetworkFailure(String category, IOException failure) throws Exception {
        failure.addSuppressed(new IOException(BODY));
        server.expect(requestTo(URL)).andRespond(withException(failure));
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
    void shouldNotReadErrorBodyOrStatusText() throws Exception {
        server.expect(requestTo(URL)).andRespond(request -> new MockClientHttpResponse(new InputStream() {
            @Override public int read() {
                throw new AssertionError("must not read upstream error body");
            }
        }, HttpStatusCode.valueOf(503)) {
            @Override public String getStatusText() {
                throw new AssertionError("must not read upstream status text");
            }
        });
        assertSafeFailure("category=HTTP_STATUS, httpStatus=503");
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 599})
    void shouldRejectOtherNonSuccessStatuses(int httpStatus) throws Exception {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(httpStatus)).body(BODY));
        assertSafeFailure("httpStatus=" + httpStatus);
    }

    @Test
    void shouldClassifyTimeoutDuringBodyRead() throws Exception {
        server.expect(requestTo(URL)).andRespond(request -> {
            var response = new MockClientHttpResponse(new InputStream() {
                @Override public int read() throws IOException { throw new SocketTimeoutException(BODY); }
            }, HttpStatusCode.valueOf(200));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        });
        assertSafeFailure("category=TIMEOUT");
    }

    @Test
    void shouldPreferNestedTimeoutOverJsonWrapperAndDropSuppressed() throws Exception {
        var jsonFailure = new com.fasterxml.jackson.core.JsonParseException(null, BODY,
                new HttpConnectTimeoutException(BODY));
        var failure = new RestClientException(BODY, jsonFailure);
        failure.addSuppressed(new IOException(BODY));
        server.expect(requestTo(URL)).andRespond(request -> { throw failure; });
        assertSafeFailure("category=CONNECT_TIMEOUT");
    }

    @Test
    void shouldSanitizeFallbackHttpException() throws Exception {
        var failure = HttpClientErrorException.create(HttpStatusCode.valueOf(401), BODY, new HttpHeaders(),
                BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        failure.addSuppressed(new IOException(BODY));
        server.expect(requestTo(URL)).andRespond(request -> { throw failure; });
        assertSafeFailure("category=HTTP_STATUS, httpStatus=401");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappedFailures")
    void shouldBoundCauseTraversalAndDiscardUntrustedChains(String scenario, RestClientException failure,
                                                          String category) throws Exception {
        server.expect(requestTo(URL)).andRespond(request -> { throw failure; });
        assertSafeFailure("category=" + category);
    }

    static Stream<Arguments> wrappedFailures() {
        var cycle = new IOException(BODY);
        var outer = new RestClientException(BODY, cycle);
        cycle.initCause(outer);
        Throwable deep = new HttpTimeoutException(BODY);
        for (int i = 0; i < 12; i++) deep = new RuntimeException(BODY, deep);
        return Stream.of(
                Arguments.of("cycle", outer, "IO_ERROR"),
                Arguments.of("depth bound", new RestClientException(BODY, deep), "HTTP_CLIENT_ERROR"),
                Arguments.of("untyped client", new RestClientException(BODY), "HTTP_CLIENT_ERROR"),
                Arguments.of("resource access", new ResourceAccessException(BODY), "IO_ERROR"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localFailures")
    void shouldNotDisguiseBusinessOrProgrammingErrors(String scenario, RuntimeException failure) {
        server.expect(requestTo(URL)).andRespond(request -> { throw failure; });
        assertThatThrownBy(() -> client.embed(List.of(INPUT))).isSameAs(failure);
    }

    static Stream<Arguments> localFailures() {
        return Stream.of(
                Arguments.of("business", new BusinessException("local business failure")),
                Arguments.of("illegal state", new IllegalStateException("local programming failure")),
                Arguments.of("null pointer", new NullPointerException("local programming failure")),
                Arguments.of("illegal argument", new IllegalArgumentException("local programming failure")));
    }

    @Test
    void shouldKeepSuccessfulEmbeddingAndIgnoredMetadataOutOfLogs() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                validResponse().replace("{\"model\"", "{\"id\":\"" + SENTINEL + "\",\"model\""),
                MediaType.APPLICATION_JSON));
        var result = client.embed(List.of(INPUT));
        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.vectors()).singleElement().satisfies(vector ->
                assertThat(vector.values()).containsExactly(0.1F, 0.2F, 0.3F));
        assertThat(result.totalTokenCount()).isEqualTo(1L);
        assertThat(appender.list.stream().map(layout::doLayout).collect(Collectors.joining()))
                .doesNotContain(SENTINEL, INPUT, API_KEY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void shouldRejectInvalidOutputWithoutLoggingIt(String scenario, ResponseCreator response, String diagnostic)
            throws Exception {
        server.expect(requestTo(URL)).andRespond(response);
        assertSafeFailure(diagnostic);
    }

    static Stream<Arguments> invalidResponses() throws Exception {
        String valid = validResponse();
        String malicious = new ObjectMapper().writeValueAsString(BODY);
        return Stream.of(
                Arguments.of("invalid JSON", withSuccess("{\"model\":" + SENTINEL + "}", MediaType.APPLICATION_JSON), "category=RESPONSE_JSON"),
                Arguments.of("invalid field type", withSuccess(valid.replace("\"index\":0", "\"index\":" + malicious), MediaType.APPLICATION_JSON), "category=RESPONSE_JSON"),
                Arguments.of("malicious model", withSuccess(valid.replace('"' + MODEL + '"', malicious), MediaType.APPLICATION_JSON), "模型与配置不一致"),
                Arguments.of("unknown media", withSuccess(BODY, MediaType.TEXT_PLAIN), "category=HTTP_CLIENT_ERROR"),
                Arguments.of("empty body", withSuccess("", MediaType.APPLICATION_JSON), "空响应"),
                Arguments.of("JSON null", withSuccess("null", MediaType.APPLICATION_JSON), "空响应"),
                Arguments.of("null data", withSuccess(valid.replace("[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]", "null"), MediaType.APPLICATION_JSON), "缺少data"),
                Arguments.of("null item", withSuccess(valid.replace("{\"index\":0,\"embedding\":[0.1,0.2,0.3]}", "null"), MediaType.APPLICATION_JSON), "空数据项"),
                Arguments.of("missing index", withSuccess(valid.replace("\"index\":0,", ""), MediaType.APPLICATION_JSON), "缺少向量索引"),
                Arguments.of("null vector", withSuccess(valid.replace("[0.1,0.2,0.3]", "null"), MediaType.APPLICATION_JSON), "缺少向量"),
                Arguments.of("null model", withSuccess(valid.replace('"' + MODEL + '"', "null"), MediaType.APPLICATION_JSON), "缺少模型名称"),
                Arguments.of("null primitive", withSuccess(valid.replace("0.2", "null"), MediaType.APPLICATION_JSON), "category=RESPONSE_JSON"));
    }

    private static String validResponse() {
        return "{\"model\":\"" + MODEL + "\",\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}],"
                + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }

    private void assertSafeFailure(String diagnostic) throws Exception {
        String response = mvc.perform(post("/probe/embedding"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("外部服务暂时不可用"))
                .andReturn().getResponse().getContentAsString();
        assertThat(controller.failure).isInstanceOf(ExternalServiceException.class);
        StringWriter stack = new StringWriter();
        controller.failure.printStackTrace(new PrintWriter(stack));
        String proxy = appender.list.stream().filter(event -> event.getThrowableProxy() != null)
                .map(event -> ThrowableProxyUtil.asString(event.getThrowableProxy())).collect(Collectors.joining());
        String rendered = appender.list.stream().map(layout::doLayout).collect(Collectors.joining());
        assertThat(response).doesNotContain(SENTINEL, API_KEY, INPUT);
        assertThat(controller.failure).hasMessageContaining(diagnostic).hasNoCause();
        assertThat(controller.failure.getSuppressed()).isEmpty();
        var errors = appender.list.stream().filter(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getFormattedMessage()).isEqualTo("外部服务异常");
        assertThat(rendered).contains("ERROR", "ExternalServiceException", diagnostic, "SpringAiEmbeddingClient");
        for (String output : List.of(response, controller.failure.toString(), stack.toString(), proxy, rendered)) {
            assertThat(output).doesNotContain(SENTINEL, API_KEY, INPUT, "Authorization:", "Bearer ",
                    "FORGED_LOG_RECORD", "\r", "\u001b", "\u0000");
        }
    }

    @RestController
    static class ProbeController {
        private final Supplier<?> action;
        private RuntimeException failure;

        ProbeController(Supplier<?> action) {
            this.action = action;
        }

        @PostMapping("/probe/embedding")
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
