package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record DashScopeEmbeddingRequest (
        String model,
        List<String> input,
        int dimensions,

        @JsonProperty("encoding_format")
        String encodingFormat
){
}
