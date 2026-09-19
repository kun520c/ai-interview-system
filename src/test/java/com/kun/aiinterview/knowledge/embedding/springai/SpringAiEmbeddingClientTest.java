package com.kun.aiinterview.knowledge.embedding.springai;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.api.OpenAiApi;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingClientTest {

    private static final String MODEL = "test-model";
    private static final String PROFILE_VERSION = "test-profile-v1";
    private static final int DIMENSION = 3;

    private EmbeddingModel embeddingModel;
    private SpringAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        client = new SpringAiEmbeddingClient(
                embeddingModel,
                testProperties(2, DIMENSION)
        );
    }

    @Test
    void shouldRestoreResponseOrderAndExposeConfiguredProfile() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(
                        embedding(1, 1.1F, 1.2F, 1.3F),
                        embedding(0, 0.1F, 0.2F, 0.3F)
                ),
                providerUsage(4, 5)
        ));

        EmbeddingBatchResult result = client.embed(
                List.of("first", "second")
        );

        assertThat(result.model()).isEqualTo(MODEL);
        assertThat(result.profileVersion()).isEqualTo(PROFILE_VERSION);
        assertThat(result.dimension()).isEqualTo(DIMENSION);
        assertThat(result.totalTokenCount()).isEqualTo(5L);
        assertThat(result.vectors())
                .extracting(vector -> vector.inputIndex())
                .containsExactly(0, 1);
        assertThat(result.vectors().get(0).values())
                .containsExactly(0.1F, 0.2F, 0.3F);
        assertThat(result.vectors().get(1).values())
                .containsExactly(1.1F, 1.2F, 1.3F);
    }

    @Test
    void shouldPreserveInputAndSplitBatchesExplicitly() {
        when(embeddingModel.call(any()))
                .thenReturn(response(
                        MODEL,
                        List.of(
                                embedding(1, 1.0F, 1.1F, 1.2F),
                                embedding(0, 0.0F, 0.1F, 0.2F)
                        ),
                        providerUsage(2, 3)
                ))
                .thenReturn(response(
                        MODEL,
                        List.of(
                                embedding(0, 2.0F, 2.1F, 2.2F),
                                embedding(1, 3.0F, 3.1F, 3.2F)
                        ),
                        providerUsage(4, 5)
                ))
                .thenReturn(response(
                        MODEL,
                        List.of(embedding(0, 4.0F, 4.1F, 4.2F)),
                        providerUsage(6, 7)
                ));

        List<String> texts = new ArrayList<>(List.of(
                "  text-0  ",
                "text-1",
                "text-2",
                "text-3",
                "text-4"
        ));
        EmbeddingBatchResult result = client.embed(texts);

        ArgumentCaptor<EmbeddingRequest> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel, times(3)).call(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(EmbeddingRequest::getInstructions)
                .containsExactly(
                        List.of("  text-0  ", "text-1"),
                        List.of("text-2", "text-3"),
                        List.of("text-4")
                );
        assertThat(texts).containsExactly(
                "  text-0  ",
                "text-1",
                "text-2",
                "text-3",
                "text-4"
        );
        assertThat(result.vectors())
                .extracting(vector -> vector.inputIndex())
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(result.totalTokenCount()).isEqualTo(15L);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "20, 1",
            "21, 2",
            "40, 2",
            "41, 3"
    })
    void shouldSplitProviderBatchesAndPreserveGlobalOrder(
            int inputCount,
            int expectedCalls
    ) {
        int batchSize = EmbeddingProperties.MAX_BATCH_SIZE;
        client = new SpringAiEmbeddingClient(
                embeddingModel,
                testProperties(batchSize, DIMENSION)
        );
        stubReversedBatches(inputCount, batchSize);

        List<String> texts = java.util.stream.IntStream.range(0, inputCount)
                .mapToObj(index -> "text-" + index)
                .toList();
        EmbeddingBatchResult result = client.embed(texts);

        ArgumentCaptor<EmbeddingRequest> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingModel, times(expectedCalls))
                .call(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(request -> request.getInstructions().size())
                .containsExactlyElementsOf(expectedBatchSizes(
                        inputCount,
                        batchSize
                ));
        assertThat(result.vectors()).hasSize(inputCount);
        assertThat(result.vectors())
                .extracting(vector -> vector.inputIndex())
                .containsExactlyElementsOf(java.util.stream.IntStream
                        .range(0, inputCount)
                        .boxed()
                        .toList());
        for (int index = 0; index < inputCount; index++) {
            assertThat(result.vectors().get(index).values())
                    .containsExactly(
                            (float) index,
                            index + 0.1F,
                            index + 0.2F
                    );
        }
    }

    @Test
    void shouldReturnNullUsageWhenAnyBatchLacksProviderTotalTokens() {
        when(embeddingModel.call(any()))
                .thenReturn(response(
                        MODEL,
                        List.of(
                                embedding(0, 0.1F, 0.2F, 0.3F),
                                embedding(1, 1.1F, 1.2F, 1.3F)
                        ),
                        providerUsage(2, 3)
                ))
                .thenReturn(response(
                        MODEL,
                        List.of(embedding(0, 2.1F, 2.2F, 2.3F)),
                        providerUsageWithoutTotal(1)
                ));

        EmbeddingBatchResult result = client.embed(
                List.of("text-0", "text-1", "text-2")
        );

        assertThat(result.totalTokenCount()).isNull();
    }

    @Test
    void shouldReturnNullUsageWhenSpringAiHasNoNativeProviderUsage() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                new EmptyUsage()
        ));

        assertThat(client.embed(List.of("text")).totalTokenCount())
                .isNull();
    }

    @Test
    void shouldRejectNullTextListWithoutCallingSpringAi() {
        assertThatThrownBy(() -> client.embed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列表");
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void shouldRejectEmptyTextListWithoutCallingSpringAi() {
        assertThatThrownBy(() -> client.embed(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空集合");
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void shouldRejectNullTextElementWithIndex() {
        List<String> texts = new ArrayList<>();
        texts.add("valid");
        texts.add(null);

        assertThatThrownBy(() -> client.embed(texts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引")
                .hasMessageContaining("1");
        verifyNoInteractions(embeddingModel);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldRejectBlankTextElementWithIndex(String blankText) {
        assertThatThrownBy(() -> client.embed(
                List.of("valid", blankText)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引")
                .hasMessageContaining("1");
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void shouldRejectDuplicateIndex() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(
                        embedding(0, 0.1F, 0.2F, 0.3F),
                        embedding(0, 1.1F, 1.2F, 1.3F)
                ),
                providerUsage(2, 2)
        ));

        assertThatThrownBy(() -> client.embed(
                List.of("first", "second")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("重复索引");
    }

    @Test
    void shouldRejectMissingResult() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                providerUsage(1, 1)
        ));

        assertThatThrownBy(() -> client.embed(
                List.of("first", "second")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("响应数量不一致");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void shouldRejectOutOfRangeIndex(int index) {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(
                        embedding(index, 0.1F, 0.2F, 0.3F),
                        embedding(0, 1.1F, 1.2F, 1.3F)
                ),
                providerUsage(2, 2)
        ));

        assertThatThrownBy(() -> client.embed(
                List.of("first", "second")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("索引超出范围");
    }

    @Test
    void shouldRejectMissingIndex() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(new Embedding(
                        new float[]{0.1F, 0.2F, 0.3F},
                        null
                )),
                providerUsage(1, 1)
        ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("缺少向量索引");
    }

    @Test
    void shouldRejectWrongDimension() {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(embedding(0, 0.1F, 0.2F)),
                providerUsage(1, 1)
        ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("向量维度不一致");
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY})
    void shouldRejectNonFiniteValue(float invalidValue) {
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(embedding(0, invalidValue, 0.2F, 0.3F)),
                providerUsage(1, 1)
        ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("非法数值");
    }

    @Test
    void shouldAcceptConfigured1024Dimension() {
        client = new SpringAiEmbeddingClient(
                embeddingModel,
                testProperties(20, 1024)
        );
        when(embeddingModel.call(any())).thenReturn(response(
                MODEL,
                List.of(new Embedding(new float[1024], 0)),
                providerUsage(1, 1)
        ));

        assertThat(client.embed(List.of("text")).vectors().getFirst()
                .values()).hasSize(1024);
    }

    @Test
    void shouldRejectMissingOrMismatchedModel() {
        when(embeddingModel.call(any()))
                .thenReturn(response(
                        null,
                        List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                        providerUsage(1, 1)
                ))
                .thenReturn(response(
                        "other-model",
                        List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                        providerUsage(1, 1)
                ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("缺少模型名称");
        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("模型与配置不一致");
    }

    @Test
    void shouldRejectNegativeProviderUsage() {
        when(embeddingModel.call(any()))
                .thenReturn(response(
                        MODEL,
                        List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                        providerUsage(-1, 1)
                ))
                .thenReturn(response(
                        MODEL,
                        List.of(embedding(0, 0.1F, 0.2F, 0.3F)),
                        providerUsage(1, -1)
                ));

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("promptTokens非法");
        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("totalTokens非法");
    }

    @Test
    void shouldMapSpringAiFailureAndKeepInputFailureUnwrapped() {
        RuntimeException providerFailure = new RuntimeException("timeout");
        when(embeddingModel.call(any())).thenThrow(providerFailure);

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("调用Embedding服务失败")
                .hasCause(providerFailure);
        assertThatThrownBy(() -> client.embed(List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(ExternalServiceException.class);
    }

    @Test
    void shouldRejectNullSpringAiResponse() {
        when(embeddingModel.call(any())).thenReturn(null);

        assertThatThrownBy(() -> client.embed(List.of("text")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("空响应");
    }

    private void stubReversedBatches(int inputCount, int batchSize) {
        OngoingStubbing<EmbeddingResponse> stubbing = null;
        for (int batchStart = 0;
             batchStart < inputCount;
             batchStart += batchSize) {
            int count = Math.min(batchSize, inputCount - batchStart);
            EmbeddingResponse response = reversedBatchResponse(
                    batchStart,
                    count
            );
            if (stubbing == null) {
                stubbing = when(embeddingModel.call(any()))
                        .thenReturn(response);
            } else {
                stubbing = stubbing.thenReturn(response);
            }
        }
    }

    private static List<Integer> expectedBatchSizes(
            int inputCount,
            int batchSize
    ) {
        List<Integer> sizes = new ArrayList<>();
        int remaining = inputCount;
        while (remaining > 0) {
            int current = Math.min(batchSize, remaining);
            sizes.add(current);
            remaining -= current;
        }
        return sizes;
    }

    private static EmbeddingResponse reversedBatchResponse(
            int batchStart,
            int count
    ) {
        List<Embedding> embeddings = new ArrayList<>(count);
        for (int localIndex = count - 1; localIndex >= 0; localIndex--) {
            int globalIndex = batchStart + localIndex;
            embeddings.add(embedding(
                    localIndex,
                    globalIndex,
                    globalIndex + 0.1F,
                    globalIndex + 0.2F
            ));
        }
        return response(
                MODEL,
                embeddings,
                providerUsage(count, count)
        );
    }

    private static Embedding embedding(int index, float... values) {
        return new Embedding(values, index);
    }

    private static EmbeddingResponse response(
            String model,
            List<Embedding> embeddings,
            Usage usage
    ) {
        return new EmbeddingResponse(
                embeddings,
                new EmbeddingResponseMetadata(model, usage)
        );
    }

    private static Usage providerUsage(int promptTokens, int totalTokens) {
        OpenAiApi.Usage nativeUsage = new OpenAiApi.Usage(
                null,
                promptTokens,
                totalTokens
        );
        return new DefaultUsage(
                promptTokens,
                0,
                totalTokens,
                nativeUsage
        );
    }

    private static Usage providerUsageWithoutTotal(int promptTokens) {
        OpenAiApi.Usage nativeUsage = new OpenAiApi.Usage(
                null,
                promptTokens,
                null
        );
        return new DefaultUsage(
                promptTokens,
                0,
                null,
                nativeUsage
        );
    }

    private static EmbeddingProperties testProperties(
            int batchSize,
            int dimension
    ) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(URI.create("http://localhost/v1"));
        properties.setApiKey("unit-test-api-key");
        properties.setModel(MODEL);
        properties.setDimension(dimension);
        properties.setBatchSize(batchSize);
        properties.setProfileVersion(PROFILE_VERSION);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
