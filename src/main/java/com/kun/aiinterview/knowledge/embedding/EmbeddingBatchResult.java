package com.kun.aiinterview.knowledge.embedding;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EmbeddingBatchResult (
        @NotNull(message = "模型不能为null")
        @NotBlank(message = "模型不能为空")
        String model,
        @NotNull(message = "向量版本不能为null")
        @NotBlank(message = "向量版本不能为空")
        String profileVersion,
        @Min(value = 1,message = "固定维度必须大于等于1")
        int dimension,
        @NotEmpty(message = "向量集合不能为空集合")
        List<EmbeddingVector> vectors,
        @Min(value = 0,message = "消耗token非null时不能小于0")
        Long totalTokenCount
){
    public EmbeddingBatchResult{
        if(model == null || model.isBlank()){
            throw new IllegalArgumentException("Embedding模型名称不能为空");
        }

        if(profileVersion == null || profileVersion.isBlank()){
            throw new IllegalArgumentException("Embedding向量版本不能为空");
        }

        if(dimension <= 0){
            throw new IllegalArgumentException("Embedding固定维度必须大于0");
        }

        if (vectors == null) {
            throw new IllegalArgumentException("Embedding向量集合不能为null");
        }

        if(vectors.isEmpty()){
            throw new IllegalArgumentException("Embedding向量集合不能为空集合");
        }

        for(EmbeddingVector vector : vectors){
            if(vector.values().size() != dimension ){
                throw new IllegalArgumentException("Embedding向量维度与批次维度不一致");
            }
        }

        if(totalTokenCount != null && totalTokenCount < 0){
            throw new IllegalArgumentException("消耗token数不能小于0");
        }

        vectors = List.copyOf(vectors);
    }
}
