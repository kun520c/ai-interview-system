package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record DashScopeEmbeddingResponse (
        List<EmbeddingData> data,
        String model,
        Usage usage
){
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(
            Integer index,
            List<Float> embedding
    ){
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage (
            @JsonProperty("prompt_tokens")
            Long promptTokens,

            @JsonProperty("total_tokens")
            Long totalTokens
    ){
    }
}
