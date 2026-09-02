package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekEvaluationClientTest {

    private static final String BASE_URL = "http://localhost";
    private static final String CHAT_COMPLETIONS_URL =
            BASE_URL + "/chat/completions";
    private static final String TEST_API_KEY = "unit-test-deepseek-api-key";
    private static final String MODEL = "test-model";
    private static final int MAX_TOKENS = 2048;
    private static final EvaluationPrompt PROMPT = new EvaluationPrompt(
            "evaluation-prompt-v1",
            "SYSTEM-PROMPT-CONTENT",
            "USER-PROMPT-CONTENT"
    );

    private static final String VALID_CHOICE = """
            {
              "index": 0,
              "finish_reason": "stop",
              "message": {
                "role": "assistant",
                "content": "{}"
              }
            }
            """;

    private ObjectMapper objectMapper;
    private MockRestServiceServer mockServer;
    private DeepSeekEvaluationClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TEST_API_KEY
                );

        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DeepSeekEvaluationClient(
                builder.build(),
                validProperties(),
                objectMapper
        );
    }

    @Test
    void givenValidPromptAndResponse_whenEvaluate_thenSendsContractAndReturnsParsedResult()
            throws JsonProcessingException {
        String rawJson = validSuggestionJson();

        mockServer.expect(once(), requestTo(CHAT_COMPLETIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TEST_API_KEY
                ))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.response_format.type")
                        .value("json_object"))
                .andExpect(jsonPath("$.thinking.type")
                        .value("disabled"))
                .andExpect(jsonPath("$.max_tokens").value(MAX_TOKENS))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content")
                        .value(PROMPT.systemPrompt()))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content")
                        .value(PROMPT.userPrompt()))
                .andRespond(withSuccess(
                        responseWithContent(rawJson),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            DeepSeekEvaluationResult result = client.evaluate(PROMPT);

            assertThat(result.model()).isEqualTo(MODEL);
            assertThat(result.finishReason()).isEqualTo("stop");
            assertThat(result.rawJson()).isEqualTo(rawJson);

            LlmEvaluationSuggestion suggestion = result.suggestion();
            assertThat(suggestion.correctnessScore()).isEqualTo(18);
            assertThat(suggestion.completenessScore()).isEqualTo(16);
            assertThat(suggestion.depthScore()).isEqualTo(14);
            assertThat(suggestion.clarityScore()).isEqualTo(12);
            assertThat(suggestion.practiceScore()).isEqualTo(10);
            assertThat(suggestion.strengths())
                    .containsExactly("准确", "表达清晰");
            assertThat(suggestion.missingPoints())
                    .containsExactly("缺少扩容细节");
            assertThat(suggestion.correction())
                    .isEqualTo("补充并发扩容风险");
            assertThat(suggestion.scoringPointResults())
                    .containsExactly(
                            new LlmEvaluationSuggestion.ScoringPointResult(
                                    101L,
                                    true,
                                    "回答提到了并发覆盖"
                            ),
                            new LlmEvaluationSuggestion.ScoringPointResult(
                                    102L,
                                    false,
                                    null
                            )
                    );
            assertThat(suggestion.followUpRecommended()).isTrue();
            assertThat(suggestion.suggestedFollowUp())
                    .isEqualTo("请说明扩容风险？");
        });
    }

    @Test
    void givenNullPrompt_whenEvaluate_thenRejectsWithoutHttpRequest() {
        verifyAfter(() -> assertThatThrownBy(() -> client.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationPrompt"));
    }

    @Test
    void givenHttpErrorStatus_whenEvaluate_thenWrapsRestClientException() {
        expectRequest().andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"supplier error\"}"));

        verifyAfter(() -> assertThatThrownBy(() -> client.evaluate(PROMPT))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("调用DeepSeek评价服务失败")
                .hasCauseInstanceOf(RuntimeException.class));
    }

    @Test
    void givenEmptyResponseBody_whenEvaluate_thenRejects() {
        expectRequest().andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        verifyAfter(() -> assertThatThrownBy(() -> client.evaluate(PROMPT))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("空响应"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponseCases")
    void givenInvalidResponseStructure_whenEvaluate_thenRejects(
            String scenario,
            String responseBody,
            String expectedMessage
    ) {
        expectRequest().andRespond(withSuccess(
                responseBody,
                MediaType.APPLICATION_JSON
        ));

        verifyAfter(() -> assertThatThrownBy(() -> client.evaluate(PROMPT))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining(expectedMessage));
    }

    @Test
    void givenMalformedSuggestionJson_whenEvaluate_thenRejectsAsExternalFailure() {
        expectRequest().andRespond(withSuccess(
                responseWithLiteralContent("{not-valid-json"),
                MediaType.APPLICATION_JSON
        ));

        verifyAfter(() -> assertThatThrownBy(() -> client.evaluate(PROMPT))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("不是可解析的JSON对象")
                .hasCauseInstanceOf(JsonProcessingException.class));
    }

    private org.springframework.test.web.client.ResponseActions expectRequest() {
        return mockServer.expect(once(), requestTo(CHAT_COMPLETIONS_URL))
                .andExpect(method(HttpMethod.POST));
    }

    private void verifyAfter(Runnable assertions) {
        Throwable primaryFailure = null;
        try {
            assertions.run();
        } catch (Throwable failure) {
            primaryFailure = failure;
        }

        try {
            mockServer.verify();
        } catch (Throwable verificationFailure) {
            if (primaryFailure == null) {
                rethrowUnchecked(verificationFailure);
            }
            primaryFailure.addSuppressed(verificationFailure);
        }

        if (primaryFailure != null) {
            rethrowUnchecked(primaryFailure);
        }
    }

    private static void rethrowUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
    }

    private String responseWithContent(String rawJson)
            throws JsonProcessingException {
        return """
                {
                  "model": "%s",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {
                        "role": "assistant",
                        "content": %s
                      }
                    }
                  ]
                }
                """.formatted(
                MODEL,
                objectMapper.writeValueAsString(rawJson)
        );
    }

    private static String responseWithLiteralContent(String content) {
        return """
                {
                  "model": "test-model",
                  "choices": [
                    {
                      "index": 0,
                      "finish_reason": "stop",
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      }
                    }
                  ]
                }
                """.formatted(content);
    }

    private static String validSuggestionJson() {
        return """
                {
                  "correctnessScore": 18,
                  "completenessScore": 16,
                  "depthScore": 14,
                  "clarityScore": 12,
                  "practiceScore": 10,
                  "strengths": ["准确", "表达清晰"],
                  "missingPoints": ["缺少扩容细节"],
                  "correction": "补充并发扩容风险",
                  "scoringPointResults": [
                    {
                      "scoringPointId": 101,
                      "covered": true,
                      "evidence": "回答提到了并发覆盖"
                    },
                    {
                      "scoringPointId": 102,
                      "covered": false,
                      "evidence": null
                    }
                  ],
                  "followUpRecommended": true,
                  "suggestedFollowUp": "请说明扩容风险？"
                }
                """;
    }

    private static Stream<Arguments> invalidResponseCases() {
        return Stream.of(
                Arguments.of(
                        "model为null",
                        response("null", "[" + VALID_CHOICE + "]"),
                        "模型名称"
                ),
                Arguments.of(
                        "model为blank",
                        response("\" \"", "[" + VALID_CHOICE + "]"),
                        "模型名称"
                ),
                Arguments.of(
                        "choices为null",
                        response("\"test-model\"", "null"),
                        "choices数量异常"
                ),
                Arguments.of(
                        "choices为空",
                        response("\"test-model\"", "[]"),
                        "choices数量异常"
                ),
                Arguments.of(
                        "choices超过一个",
                        response(
                                "\"test-model\"",
                                "[" + VALID_CHOICE + "," + VALID_CHOICE + "]"
                        ),
                        "choices数量异常"
                ),
                Arguments.of(
                        "choice为null",
                        response("\"test-model\"", "[null]"),
                        "空choice"
                ),
                Arguments.of(
                        "choice index为null",
                        response(
                                "\"test-model\"",
                                "[" + choice("null", "\"stop\"", validMessage()) + "]"
                        ),
                        "choice索引异常"
                ),
                Arguments.of(
                        "choice index不为0",
                        response(
                                "\"test-model\"",
                                "[" + choice("1", "\"stop\"", validMessage()) + "]"
                        ),
                        "choice索引异常"
                ),
                Arguments.of(
                        "finish reason为length",
                        response(
                                "\"test-model\"",
                                "[" + choice("0", "\"length\"", validMessage()) + "]"
                        ),
                        "finishReason=length"
                ),
                Arguments.of(
                        "finish reason为其他非stop值",
                        response(
                                "\"test-model\"",
                                "[" + choice(
                                        "0",
                                        "\"content_filter\"",
                                        validMessage()
                                ) + "]"
                        ),
                        "finishReason=content_filter"
                ),
                Arguments.of(
                        "message为null",
                        response(
                                "\"test-model\"",
                                "[" + choice("0", "\"stop\"", "null") + "]"
                        ),
                        "缺少message"
                ),
                Arguments.of(
                        "message role不是assistant",
                        response(
                                "\"test-model\"",
                                "[" + choice(
                                        "0",
                                        "\"stop\"",
                                        message("\"user\"", "\"{}\"")
                                ) + "]"
                        ),
                        "message role异常"
                ),
                Arguments.of(
                        "message content为null",
                        response(
                                "\"test-model\"",
                                "[" + choice(
                                        "0",
                                        "\"stop\"",
                                        message("\"assistant\"", "null")
                                ) + "]"
                        ),
                        "缺少评价内容"
                ),
                Arguments.of(
                        "message content为blank",
                        response(
                                "\"test-model\"",
                                "[" + choice(
                                        "0",
                                        "\"stop\"",
                                        message("\"assistant\"", "\" \"")
                                ) + "]"
                        ),
                        "缺少评价内容"
                )
        );
    }

    private static String response(String modelJson, String choicesJson) {
        return """
                {
                  "model": %s,
                  "choices": %s
                }
                """.formatted(modelJson, choicesJson);
    }

    private static String choice(
            String indexJson,
            String finishReasonJson,
            String messageJson
    ) {
        return """
                {
                  "index": %s,
                  "finish_reason": %s,
                  "message": %s
                }
                """.formatted(indexJson, finishReasonJson, messageJson);
    }

    private static String validMessage() {
        return message("\"assistant\"", "\"{}\"");
    }

    private static String message(String roleJson, String contentJson) {
        return """
                {
                  "role": %s,
                  "content": %s
                }
                """.formatted(roleJson, contentJson);
    }

    private static DeepSeekProperties validProperties() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        properties.setApiKey(TEST_API_KEY);
        properties.setModel(MODEL);
        properties.setMaxTokens(MAX_TOKENS);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
