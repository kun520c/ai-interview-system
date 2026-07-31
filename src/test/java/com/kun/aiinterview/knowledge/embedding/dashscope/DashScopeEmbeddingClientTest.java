package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

class DashScopeEmbeddingClientTest {

    private static final String BASE_URL = "http://localhost";
    private static final String EMBEDDINGS_URL =
            BASE_URL + "/embeddings";
    private static final String TEST_API_KEY = "unit-test-api-key";
    private static final String MODEL = "test-model";
    private static final int DIMENSION = 3;

    private MockRestServiceServer mockServer;
    private DashScopeEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TEST_API_KEY
                );

        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder.build();
        client = new DashScopeEmbeddingClient(
                restClient,
                testProperties(2)
        );
    }

    @Test
    void shouldPostCompatibleRequestAndRestoreSupplierIndexOrder() {
        expectRequest(List.of("first", "second"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 1, "embedding": [1.1, 1.2, 1.3]},
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 4, "total_tokens": 5}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            EmbeddingBatchResult result = client.embed(
                    List.of("first", "second")
            );

            assertThat(result.model()).isEqualTo(MODEL);
            assertThat(result.profileVersion())
                    .isEqualTo("test-profile-v1");
            assertThat(result.dimension()).isEqualTo(DIMENSION);
            assertThat(result.totalTokenCount()).isEqualTo(5L);
            assertThat(result.vectors())
                    .extracting(vector -> vector.inputIndex())
                    .containsExactly(0, 1);
            assertThat(result.vectors().get(0).values())
                    .containsExactly(0.1F, 0.2F, 0.3F);
            assertThat(result.vectors().get(1).values())
                    .containsExactly(1.1F, 1.2F, 1.3F);
        });
    }

    @Test
    void shouldPreserveInputTextAndLeaveCallerListUnmodified() {
        List<String> texts = new ArrayList<>(
                List.of("  leading and trailing  ")
        );
        expectRequest(List.copyOf(texts))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 2, "total_tokens": 2}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            client.embed(texts);

            assertThat(texts)
                    .containsExactly("  leading and trailing  ");
        });
    }

    @Test
    void shouldSplitFiveInputsIntoThreeBatchesAndAccumulateTokens() {
        expectRequest(List.of("text-0", "text-1"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 1, "embedding": [1.1, 1.2, 1.3]},
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 2, "total_tokens": 3}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));
        expectRequest(List.of("text-2", "text-3"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [2.1, 2.2, 2.3]},
                                  {"index": 1, "embedding": [3.1, 3.2, 3.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 4, "total_tokens": 5}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));
        expectRequest(List.of("text-4"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [4.1, 4.2, 4.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 6, "total_tokens": 7}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            EmbeddingBatchResult result = client.embed(
                    List.of(
                            "text-0",
                            "text-1",
                            "text-2",
                            "text-3",
                            "text-4"
                    )
            );

            assertThat(result.vectors())
                    .extracting(vector -> vector.inputIndex())
                    .containsExactly(0, 1, 2, 3, 4);
            assertThat(result.vectors())
                    .extracting(vector -> vector.values().getFirst())
                    .containsExactly(0.1F, 1.1F, 2.1F, 3.1F, 4.1F);
            assertThat(result.totalTokenCount()).isEqualTo(15L);
        });
    }

    @Test
    void shouldReturnNullTokenCountWhenAnyBatchUsageIsMissing() {
        expectRequest(List.of("text-0", "text-1"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]},
                                  {"index": 1, "embedding": [1.1, 1.2, 1.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 2, "total_tokens": 3}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));
        expectRequest(List.of("text-2"))
                .andRespond(withSuccess(
                        responseWithoutUsage(
                                """
                                [
                                  {"index": 0, "embedding": [2.1, 2.2, 2.3]}
                                ]
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            EmbeddingBatchResult result = client.embed(
                    List.of("text-0", "text-1", "text-2")
            );

            assertThat(result.totalTokenCount()).isNull();
        });
    }

    @Test
    void shouldReturnNullTokenCountWhenAnyBatchTotalTokensIsMissing() {
        expectRequest(List.of("text-0", "text-1"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]},
                                  {"index": 1, "embedding": [1.1, 1.2, 1.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 2, "total_tokens": 3}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));
        expectRequest(List.of("text-2"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [2.1, 2.2, 2.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 1}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> {
            EmbeddingBatchResult result = client.embed(
                    List.of("text-0", "text-1", "text-2")
            );

            assertThat(result.totalTokenCount()).isNull();
        });
    }

    @Test
    void shouldRejectNullTextListWithoutSendingRequest() {
        verifyAfter(() -> assertThatThrownBy(() -> client.embed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列表"));
    }

    @Test
    void shouldRejectEmptyTextListWithoutSendingRequest() {
        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of())
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空集合"));
    }

    @Test
    void shouldRejectNullTextElementAndReportItsIndex() {
        List<String> texts = new ArrayList<>();
        texts.add("valid");
        texts.add(null);

        verifyAfter(() -> assertThatThrownBy(() -> client.embed(texts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引")
                .hasMessageContaining("1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldRejectBlankTextElementAndReportItsIndex(String blankText) {
        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("valid", blankText))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引")
                .hasMessageContaining("1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void shouldWrapHttpErrorStatus(int statusCode) {
        expectRequest(List.of("text"))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"message": "supplier error"}
                                """));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("text"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("调用Embedding服务失败")
                .hasCauseInstanceOf(RuntimeException.class));
    }

    @Test
    void shouldRejectEmptyResponseBody() {
        expectRequest(List.of("text"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("text"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("空响应"));
    }

    @Test
    void shouldWrapMalformedJsonResponse() {
        expectRequest(List.of("text"))
                .andRespond(withSuccess(
                        "{not-valid-json",
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("text"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("调用Embedding服务失败")
                .hasCauseInstanceOf(RuntimeException.class));
    }

    @Test
    void shouldRejectDuplicateSupplierIndex() {
        expectRequest(List.of("first", "second"))
                .andRespond(withSuccess(
                        validResponse(
                                """
                                [
                                  {"index": 0, "embedding": [0.1, 0.2, 0.3]},
                                  {"index": 0, "embedding": [1.1, 1.2, 1.3]}
                                ]
                                """,
                                """
                                {"prompt_tokens": 2, "total_tokens": 2}
                                """
                        ),
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("first", "second"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("重复索引"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidResponseCases")
    void shouldRejectInvalidSupplierResponse(
            String scenario,
            String responseBody,
            String expectedMessage
    ) {
        expectRequest(List.of("text"))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("text"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining(expectedMessage));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity"})
    void shouldRejectNonFiniteEmbeddingValue(String nonFiniteValue) {
        String responseBody = """
                {
                  "data": [
                    {
                      "index": 0,
                      "embedding": [%s, 0.2, 0.3]
                    }
                  ],
                  "model": "%s",
                  "usage": {
                    "prompt_tokens": 1,
                    "total_tokens": 1
                  }
                }
                """.formatted(nonFiniteValue, MODEL);
        expectRequest(List.of("text"))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));

        verifyAfter(() -> assertThatThrownBy(
                () -> client.embed(List.of("text"))
        )
                .isInstanceOf(ExternalServiceException.class)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .isIn(
                                "调用Embedding服务失败",
                                "Embedding向量包含非法数值，向量索引：0，维度索引0"
                        )));
    }

    private ResponseActions expectRequest(List<String> expectedTexts) {
        ResponseActions actions = mockServer.expect(
                        once(),
                        requestTo(EMBEDDINGS_URL)
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TEST_API_KEY
                ))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.dimensions").value(DIMENSION))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andExpect(jsonPath("$.input").isArray())
                .andExpect(jsonPath("$.input.length()")
                        .value(expectedTexts.size()));

        for (int index = 0; index < expectedTexts.size(); index++) {
            actions.andExpect(jsonPath("$.input[" + index + "]")
                    .value(expectedTexts.get(index)));
        }
        return actions;
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

    private static EmbeddingProperties testProperties(int batchSize) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        properties.setApiKey(TEST_API_KEY);
        properties.setModel(MODEL);
        properties.setDimension(DIMENSION);
        properties.setBatchSize(batchSize);
        properties.setProfileVersion("test-profile-v1");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static String validResponse(
            String dataJson,
            String usageJson
    ) {
        return """
                {
                  "data": %s,
                  "model": "%s",
                  "usage": %s,
                  "id": "response-id",
                  "object": "list"
                }
                """.formatted(dataJson, MODEL, usageJson);
    }

    private static String responseWithoutUsage(String dataJson) {
        return """
                {
                  "data": %s,
                  "model": "%s"
                }
                """.formatted(dataJson, MODEL);
    }

    private static Stream<Arguments> invalidResponseCases() {
        return Stream.of(
                Arguments.of(
                        "missing model",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "缺少模型名称"
                ),
                Arguments.of(
                        "mismatched model",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "other-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "模型与配置不一致"
                ),
                Arguments.of(
                        "missing data",
                        """
                        {
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "缺少data"
                ),
                Arguments.of(
                        "too few data items",
                        """
                        {
                          "data": [],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "响应数量不一致"
                ),
                Arguments.of(
                        "too many data items",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2, 0.3]},
                            {"index": 1, "embedding": [1.1, 1.2, 1.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "响应数量不一致"
                ),
                Arguments.of(
                        "null data item",
                        """
                        {
                          "data": [null],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "空数据项"
                ),
                Arguments.of(
                        "missing index",
                        """
                        {
                          "data": [
                            {"embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "索引"
                ),
                Arguments.of(
                        "negative index",
                        """
                        {
                          "data": [
                            {"index": -1, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "索引超出范围"
                ),
                Arguments.of(
                        "out of range index",
                        """
                        {
                          "data": [
                            {"index": 1, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "索引超出范围"
                ),
                Arguments.of(
                        "missing embedding",
                        """
                        {
                          "data": [{"index": 0}],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "缺少向量"
                ),
                Arguments.of(
                        "wrong embedding dimension",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "向量维度不一致"
                ),
                Arguments.of(
                        "null embedding value",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, null, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": 1}
                        }
                        """,
                        "非法数值"
                ),
                Arguments.of(
                        "negative prompt tokens",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {
                            "prompt_tokens": -1,
                            "total_tokens": 1
                          }
                        }
                        """,
                        "promptTokens非法"
                ),
                Arguments.of(
                        "negative total tokens",
                        """
                        {
                          "data": [
                            {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                          ],
                          "model": "test-model",
                          "usage": {"total_tokens": -1}
                        }
                        """,
                        "totalTokens非法"
                )
        );
    }
}
