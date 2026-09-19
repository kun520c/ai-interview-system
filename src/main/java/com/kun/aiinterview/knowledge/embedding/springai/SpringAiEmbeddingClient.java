package com.kun.aiinterview.knowledge.embedding.springai;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public SpringAiEmbeddingClient(
            EmbeddingModel embeddingModel,
            EmbeddingProperties properties
    ) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public EmbeddingBatchResult embed(List<String> texts) {
        List<String> validatedTexts = validateAndCopy(texts);
        List<EmbeddingVector> allVectors = new ArrayList<>(
                validatedTexts.size()
        );

        long accumulatedTokenCount = 0L;
        boolean tokenCountAvailable = true;
        int batchSize = properties.getBatchSize();

        for (int batchStart = 0;
             batchStart < validatedTexts.size();
             batchStart += batchSize) {
            int batchEnd = Math.min(
                    batchStart + batchSize,
                    validatedTexts.size()
            );
            List<String> batchTexts = List.copyOf(
                    validatedTexts.subList(batchStart, batchEnd)
            );

            EmbeddingResponse response = requestBatch(batchTexts);
            allVectors.addAll(validateAndConvertResponse(
                    response,
                    batchTexts.size(),
                    batchStart
            ));

            Long batchTokenCount = extractTokenCount(response);
            if (batchTokenCount == null) {
                tokenCountAvailable = false;
            } else {
                accumulatedTokenCount += batchTokenCount;
            }
        }

        return new EmbeddingBatchResult(
                properties.getModel(),
                properties.getProfileVersion(),
                properties.getDimension(),
                allVectors,
                tokenCountAvailable ? accumulatedTokenCount : null
        );
    }

    private List<String> validateAndCopy(List<String> texts) {
        if (texts == null) {
            throw new IllegalArgumentException(
                    "Embedding输入文本列表不能为空"
            );
        }
        if (texts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Embedding输入文本列表不能是空集合"
            );
        }

        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "Embedding输入文本不能是空白内容,索引：" + index
                );
            }
        }
        return List.copyOf(texts);
    }

    private EmbeddingResponse requestBatch(List<String> batchTexts) {
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(batchTexts, null)
            );
            if (response == null) {
                throw new ExternalServiceException(
                        "Embedding服务器返回空响应"
                );
            }
            return response;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(
                    "调用Embedding服务失败",
                    exception
            );
        }
    }

    private List<EmbeddingVector> validateAndConvertResponse(
            EmbeddingResponse response,
            int expectedCount,
            int batchStart
    ) {
        validateResponseModel(response);

        List<Embedding> results = response.getResults();
        if (results == null) {
            throw new ExternalServiceException("Embedding响应缺少data");
        }
        if (results.size() != expectedCount) {
            throw new ExternalServiceException(
                    "Embedding响应数量不一致，预期:"
                            + expectedCount
                            + ",实际:"
                            + results.size()
            );
        }

        List<EmbeddingVector> orderedVectors = new ArrayList<>(
                Collections.nCopies(expectedCount, null)
        );
        for (Embedding result : results) {
            if (result == null) {
                throw new ExternalServiceException(
                        "Embedding响应包含空数据项"
                );
            }

            Integer localIndex = result.getIndex();
            validateResponseIndex(
                    localIndex,
                    expectedCount,
                    orderedVectors
            );
            List<Float> values = validateAndCopyEmbeddingValues(
                    result.getOutput(),
                    localIndex
            );

            orderedVectors.set(
                    localIndex,
                    new EmbeddingVector(batchStart + localIndex, values)
            );
        }

        if (orderedVectors.stream().anyMatch(vector -> vector == null)) {
            throw new ExternalServiceException(
                    "Embedding响应缺少向量索引"
            );
        }
        return List.copyOf(orderedVectors);
    }

    private void validateResponseModel(EmbeddingResponse response) {
        EmbeddingResponseMetadata metadata = response.getMetadata();
        String responseModel = metadata == null
                ? null
                : metadata.getModel();

        if (responseModel == null || responseModel.isBlank()) {
            throw new ExternalServiceException(
                    "Embedding响应缺少模型名称"
            );
        }
        if (!properties.getModel().equals(responseModel)) {
            throw new ExternalServiceException(
                    "Embedding响应模型与配置不一致，配置模型："
                            + properties.getModel()
                            + "响应模型："
                            + responseModel
            );
        }
    }

    private void validateResponseIndex(
            Integer localIndex,
            int expectedCount,
            List<EmbeddingVector> orderedVectors
    ) {
        if (localIndex == null) {
            throw new ExternalServiceException(
                    "Embedding响应缺少向量索引"
            );
        }
        if (localIndex < 0 || localIndex >= expectedCount) {
            throw new ExternalServiceException(
                    "Embedding响应索引超出范围：" + localIndex
            );
        }
        if (orderedVectors.get(localIndex) != null) {
            throw new ExternalServiceException(
                    "Embedding响应包含重复索引" + localIndex
            );
        }
    }

    private List<Float> validateAndCopyEmbeddingValues(
            float[] values,
            int localIndex
    ) {
        if (values == null) {
            throw new ExternalServiceException(
                    "Embedding响应缺少向量，索引：" + localIndex
            );
        }
        if (values.length != properties.getDimension()) {
            throw new ExternalServiceException(
                    "Embedding向量维度不一致，索引："
                            + localIndex
                            + "，预期维度："
                            + properties.getDimension()
                            + "，实际维度："
                            + values.length
            );
        }

        List<Float> copiedValues = new ArrayList<>(values.length);
        for (int dimensionIndex = 0;
             dimensionIndex < values.length;
             dimensionIndex++) {
            float value = values[dimensionIndex];
            if (!Float.isFinite(value)) {
                throw new ExternalServiceException(
                        "Embedding向量包含非法数值，向量索引："
                                + localIndex
                                + "，维度索引"
                                + dimensionIndex
                );
            }
            copiedValues.add(value);
        }
        return List.copyOf(copiedValues);
    }

    private Long extractTokenCount(EmbeddingResponse response) {
        EmbeddingResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        if (usage == null) {
            return null;
        }

        Object nativeUsage = usage.getNativeUsage();
        if (!(nativeUsage instanceof OpenAiApi.Usage openAiUsage)) {
            return null;
        }

        Integer promptTokens = openAiUsage.promptTokens();
        Integer totalTokens = openAiUsage.totalTokens();
        if (promptTokens != null && promptTokens < 0) {
            throw new ExternalServiceException(
                    "Embedding响应promptTokens非法"
            );
        }
        if (totalTokens != null && totalTokens < 0) {
            throw new ExternalServiceException(
                    "Embedding响应totalTokens非法"
            );
        }
        return totalTokens == null ? null : totalTokens.longValue();
    }
}
