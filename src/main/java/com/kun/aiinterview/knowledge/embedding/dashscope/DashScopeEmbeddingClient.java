package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public DashScopeEmbeddingClient(
            @Qualifier("embeddingRestClient")
            RestClient restClient,
            EmbeddingProperties properties
    ){
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public EmbeddingBatchResult embed(List<String> texts) {
        List<String> validatedTexts = validateAndCopy(texts);

        List<EmbeddingVector> allVectors =
                new ArrayList<>(validatedTexts.size());

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

            DashScopeEmbeddingResponse response =
                    requestBatch(batchTexts);

            List<EmbeddingVector> batchVectors =
                    validateAndConvertResponse(
                            response,
                            batchTexts.size(),
                            batchStart
                    );

            allVectors.addAll(batchVectors);

            Long batchTokenCount = extractTokenCount(response);

            if (batchTokenCount == null) {
                tokenCountAvailable = false;
            } else {
                accumulatedTokenCount += batchTokenCount;
            }
        }

        Long totalTokenCount = tokenCountAvailable
                ? accumulatedTokenCount
                : null;

        return new EmbeddingBatchResult(
                properties.getModel(),
                properties.getProfileVersion(),
                properties.getDimension(),
                allVectors,
                totalTokenCount
        );
    }

    private List<String> validateAndCopy(List<String> texts) {
        if(texts == null){
            throw new IllegalArgumentException("Embedding输入文本列表不能为空");
        }

        if(texts.isEmpty()){
            throw new IllegalArgumentException("Embedding输入文本列表不能是空集合");
        }

        for(int index = 0;index < texts.size();index++){
            String text = texts.get(index);

            if(text == null || text.isBlank()){
                throw new IllegalArgumentException("Embedding输入文本不能是空白内容,索引：" + index);
            }
        }
        return List.copyOf(texts);
    }

    private DashScopeEmbeddingResponse requestBatch(
            List<String> batchTexts
    ){
        DashScopeEmbeddingRequest request =
                new DashScopeEmbeddingRequest(
                        properties.getModel(),
                        batchTexts,
                        properties.getDimension(),
                        "float"
                );

        try{
            DashScopeEmbeddingResponse response = restClient
                    .post()
                    .uri(EMBEDDINGS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(DashScopeEmbeddingResponse.class);

            if(response == null){
                throw new ExternalServiceException("Embedding服务器返回空响应");
            }

            return response;
        }catch (RestClientException exception){
            throw new ExternalServiceException(
                    "调用Embedding服务失败",
                    exception
            );
        }
    }

    private List<EmbeddingVector> validateAndConvertResponse(
            DashScopeEmbeddingResponse response,
            int expectedCount,
            int batchStart
    ){
        validateResponseModel(response);

        if(response.data() == null){
            throw new ExternalServiceException("Embedding响应缺少data");
        }

        if(response.data().size() != expectedCount){
            throw new ExternalServiceException(
                    "Embedding响应数量不一致，预期:"
                    + expectedCount
                    + ",实际:"
                    + response.data().size()
            );
        }

        List<EmbeddingVector> orderedVectors = new ArrayList<>(Collections.nCopies(expectedCount,null));

        for(DashScopeEmbeddingResponse.EmbeddingData data : response.data()){
            if(data == null) {
                throw  new ExternalServiceException("Embedding响应包含空数据项");
            }

            Integer localIndex = data.index();

            validateResponseIndex(
                    localIndex,
                    expectedCount,
                    orderedVectors
            );

            validateEmbeddingValues(
                    data.embedding(),
                    localIndex
            );

            int globalIndex = batchStart + localIndex;

            orderedVectors.set(
                    localIndex,
                    new EmbeddingVector(
                            globalIndex,
                            data.embedding()
                    )
            );
        }

        return List.copyOf(orderedVectors);
    }

    private void validateResponseModel(DashScopeEmbeddingResponse response){
        if(response.model() == null || response.model().isBlank()){
            throw new ExternalServiceException("Embedding响应缺少模型名称");
        }

        if(!properties.getModel().equals(response.model())){
            throw new ExternalServiceException(
                    "Embedding响应模型与配置不一致，配置模型："
                    + properties.getModel()
                    + "响应模型："
                    + response.model()
            );
        }
    }
    private void validateResponseIndex(
            Integer localIndex,
            int expectedCount,
            List<EmbeddingVector> orderedVectors
    ){
        if(localIndex == null){
            throw new ExternalServiceException("Embedding响应少向量索引");
        }

        if(localIndex < 0 || localIndex >= expectedCount){
            throw new ExternalServiceException("Embedding响应索引超出范围："
                                                    + localIndex
            );
        }

        if(orderedVectors.get(localIndex) != null){
            throw new ExternalServiceException(
                    "Embedding响应包含重复索引"
                            +localIndex
            );
        }
    }

    private void validateEmbeddingValues(
            List<Float> values,
            int localIndex
    ){
        if(values == null){
            throw new ExternalServiceException(
                    "Embedding响应缺少向量，索引："
                            + localIndex
            );
        }

        if(values.size() != properties.getDimension()){
            throw new ExternalServiceException(
                    "Embedding向量维度不一致，索引："
                        + localIndex
                        + "，预期维度："
                        + properties.getDimension()
                        + "，实际维度："
                        + values.size()
            );
        }

        for(int dimensionIndex = 0;dimensionIndex < values.size();dimensionIndex++){

            Float value = values.get(dimensionIndex);

            if(value == null || !Float.isFinite(value)){
                throw new ExternalServiceException(
                        "Embedding向量包含非法数值，向量索引："
                            + localIndex
                            + "，维度索引"
                            + dimensionIndex
                );
            }
        }
    }

    private Long extractTokenCount(DashScopeEmbeddingResponse response){
        if(response.usage() == null){
            return null;
        }

        Long promptTokens = response.usage().promptTokens();
        Long totalTokens = response.usage().totalTokens();

        if(promptTokens != null && promptTokens < 0){
            throw new ExternalServiceException(
                    "Embedding响应promptTokens非法"
            );
        }

        if(totalTokens != null && totalTokens < 0){
            throw new ExternalServiceException(
                    "Embedding响应totalTokens非法"
            );
        }

        return totalTokens;
    }
}