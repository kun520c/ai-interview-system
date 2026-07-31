package com.kun.aiinterview.knowledge.embedding;

import java.util.List;

public record EmbeddingVector (
        int inputIndex,
        List<Float> values
){
    public EmbeddingVector{
        if (inputIndex < 0) {
            throw new IllegalArgumentException("Embedding 输入索引不能小于0");
        }

        if (values == null) {
            throw new IllegalArgumentException("Embedding向量不能为空");
        }

        if(values.isEmpty()){
            throw new IllegalArgumentException("Embedding向量不能是空列表");
        }

        values = List.copyOf(values);
    }
}
