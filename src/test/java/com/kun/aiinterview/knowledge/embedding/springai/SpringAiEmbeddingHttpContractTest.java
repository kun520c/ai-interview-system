package com.kun.aiinterview.knowledge.embedding.springai;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

class SpringAiEmbeddingHttpContractTest {

    private static final String BASE_URL = "http://localhost:18080/v1";
    private static final String API_KEY = "http-contract-api-key";
    private static final String MODEL = "qwen3.7-text-embedding";
    private static final int DIMENSION = 1024;
    private static final String EMBEDDINGS_URL = BASE_URL + "/embeddings";

    private MockRestServiceServer mockServer;
    private SpringAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        EmbeddingProperties properties = testProperties();
        RestClient.Builder restClientBuilder =
                EmbeddingConfiguration.createEmbeddingRestClientBuilder(
                        properties
                );
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenAiApi openAiApi = EmbeddingConfiguration.createOpenAiApi(
                properties,
                restClientBuilder
        );
        EmbeddingModel embeddingModel =
                EmbeddingConfiguration.createEmbeddingModel(
                        openAiApi,
                        properties
                );
        client = new SpringAiEmbeddingClient(embeddingModel, properties);
    }

    @Test
    void shouldSendExactBailianOpenAiCompatibleHttpContract() {
        expectOnce().andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + API_KEY
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.input[0]")
                        .value("  preserve whitespace  "))
                .andExpect(jsonPath("$.dimensions").value(DIMENSION))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andRespond(withSuccess(
                        validResponse(0),
                        MediaType.APPLICATION_JSON
                ));

        EmbeddingBatchResult result = client.embed(
                List.of("  preserve whitespace  ")
        );

        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.profileVersion())
                .isEqualTo("qwen3.7-text-embedding-1024-dense-v1");
        assertThat(result.dimension()).isEqualTo(DIMENSION);
        assertThat(result.totalTokenCount()).isEqualTo(9L);
        assertThat(result.vectors()).singleElement().satisfies(vector -> {
            assertThat(vector.inputIndex()).isZero();
            assertThat(vector.values()).hasSize(DIMENSION);
            assertThat(vector.values()).doesNotContainNull();
        });
        mockServer.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void shouldMapHttpErrorWithoutRetrying(int statusCode) {
        expectOnce().andRespond(withStatus(HttpStatus.valueOf(statusCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":{"message":"provider-error"}}
                        """));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectEmptyHttpBody() {
        expectOnce().andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(""));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectMalformedJson() {
        expectOnce().andRespond(withSuccess(
                "{not-json",
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectNullDataArray() {
        expectOnce().andRespond(withSuccess(
                responseWithData("null"),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectMissingDataArray() {
        expectOnce().andRespond(withSuccess(
                """
                        {
                          "object": "list",
                          "model": "%s",
                          "usage": {
                            "prompt_tokens": 1,
                            "total_tokens": 1
                          }
                        }
                        """.formatted(MODEL),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectNullDataItem() {
        expectOnce().andRespond(withSuccess(
                responseWithData("[null]"),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectMissingIndex() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(null, array(floats(DIMENSION, -1))),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectDuplicateIndex() {
        expectOnce().andRespond(withSuccess(
                twoItemResponse(0, 0),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("A", "B"));
    }

    @Test
    void shouldRejectNegativeIndex() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(-1, array(floats(DIMENSION, -1))),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectOutOfRangeIndex() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(1, array(floats(DIMENSION, -1))),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectMissingEmbedding() {
        expectOnce().andRespond(withSuccess(
                """
                        {
                          "object": "list",
                          "model": "%s",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 1,
                            "total_tokens": 1
                          }
                        }
                        """.formatted(MODEL),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectNullEmbedding() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(0, "null"),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectNullEmbeddingElementWithoutAcceptingVector() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(0, array(floats(DIMENSION, 1))),
                MediaType.APPLICATION_JSON
        ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class);
        mockServer.verify();
    }

    @Test
    void shouldRejectWrongDimension() {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(0, array(floats(DIMENSION - 1, -1))),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
    void shouldRejectNonFiniteWireTokens(String token) {
        expectOnce().andRespond(withSuccess(
                dataItemResponse(0, array(floatsWithToken(token))),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRejectWrongResponseModel() {
        expectOnce().andRespond(withSuccess(
                validResponse(0).replace(
                        "\"model\": \"" + MODEL + "\"",
                        "\"model\": \"other-embedding-model\""
                ),
                MediaType.APPLICATION_JSON
        ));

        assertExternalFailure(List.of("text"));
    }

    @Test
    void shouldRestoreProviderIndexOrderOverHttp() {
        expectOnce().andRespond(withSuccess(
                twoItemResponse(1, 0),
                MediaType.APPLICATION_JSON
        ));

        EmbeddingBatchResult result = client.embed(List.of("A", "B"));

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0).inputIndex()).isZero();
        assertThat(result.vectors().get(0).values().getFirst())
                .isEqualTo(0.25F);
        assertThat(result.vectors().get(1).inputIndex()).isEqualTo(1);
        assertThat(result.vectors().get(1).values().getFirst())
                .isEqualTo(0.5F);
        mockServer.verify();
    }

    @Test
    void shouldKeepTotalTokenCountNullWhenUsageOmitsTotalTokens() {
        expectOnce().andRespond(withSuccess(
                """
                        {
                          "object": "list",
                          "model": "%s",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0,
                              "embedding": %s
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 1
                          }
                        }
                        """.formatted(
                        MODEL,
                        array(floats(DIMENSION, -1))
                ),
                MediaType.APPLICATION_JSON
        ));

        EmbeddingBatchResult result = client.embed(List.of("text"));

        assertThat(result.vectors()).hasSize(1);
        assertThat(result.totalTokenCount()).isNull();
        mockServer.verify();
    }

    private ResponseActions expectOnce() {
        return mockServer.expect(once(), requestTo(EMBEDDINGS_URL));
    }

    private void assertExternalFailure(List<String> texts) {
        assertThatThrownBy(() -> client.embed(texts))
                .isInstanceOf(ExternalServiceException.class);
        mockServer.verify();
    }

    private static String validResponse(int index) {
        return dataItemResponse(index, array(floats(DIMENSION, -1)));
    }

    private static String dataItemResponse(
            Integer index,
            String embeddingJson
    ) {
        String indexJson = index == null
                ? ""
                : "\"index\": " + index + ",";
        return """
                {
                  "object": "list",
                  "model": "%s",
                  "data": [
                    {
                      "object": "embedding",
                      %s
                      "embedding": %s
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 9,
                    "total_tokens": 9
                  }
                }
                """.formatted(MODEL, indexJson, embeddingJson);
    }

    private static String twoItemResponse(int firstIndex, int secondIndex) {
        return """
                {
                  "object": "list",
                  "model": "%s",
                  "data": [
                    {
                      "object": "embedding",
                      "index": %d,
                      "embedding": %s
                    },
                    {
                      "object": "embedding",
                      "index": %d,
                      "embedding": %s
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 2,
                    "total_tokens": 2
                  }
                }
                """.formatted(
                MODEL,
                firstIndex,
                array(firstIndex == 1
                        ? floatsWithFirst(0.5F)
                        : floatsWithFirst(0.25F)),
                secondIndex,
                array(secondIndex == 0
                        ? floatsWithFirst(0.25F)
                        : floatsWithFirst(0.5F))
        );
    }

    private static String responseWithData(String dataJson) {
        return """
                {
                  "object": "list",
                  "model": "%s",
                  "data": %s,
                  "usage": {
                    "prompt_tokens": 1,
                    "total_tokens": 1
                  }
                }
                """.formatted(MODEL, dataJson);
    }

    private static String array(String values) {
        return "[" + values + "]";
    }

    private static String floats(int dimension, int nullIndex) {
        return IntStream.range(0, dimension)
                .mapToObj(index -> {
                    if (index == nullIndex) {
                        return "null";
                    }
                    return index == 0 ? "0.25" : "0";
                })
                .collect(Collectors.joining(","));
    }

    private static String floatsWithToken(String token) {
        return IntStream.range(0, DIMENSION)
                .mapToObj(index -> {
                    if (index == 1) {
                        return token;
                    }
                    return index == 0 ? "0.25" : "0";
                })
                .collect(Collectors.joining(","));
    }

    private static String floatsWithFirst(float firstValue) {
        return IntStream.range(0, DIMENSION)
                .mapToObj(index -> index == 0
                        ? Float.toString(firstValue)
                        : "0")
                .collect(Collectors.joining(","));
    }

    private static EmbeddingProperties testProperties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(URI.create(BASE_URL));
        properties.setApiKey(API_KEY);
        properties.setModel(MODEL);
        properties.setDimension(DIMENSION);
        properties.setBatchSize(EmbeddingProperties.MAX_BATCH_SIZE);
        properties.setProfileVersion(
                "qwen3.7-text-embedding-1024-dense-v1"
        );
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(20));
        return properties;
    }
}
