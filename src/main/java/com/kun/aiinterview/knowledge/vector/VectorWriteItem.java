package com.kun.aiinterview.knowledge.vector;

import java.util.List;

public record VectorWriteItem (
        String vectorId,
        Long documentId,
        int chunkIndex,
        String embeddingVersion,
        List<Float> values
){
    public VectorWriteItem{
        if(vectorId == null || vectorId.isBlank()){
            throw new IllegalArgumentException("向量Id不能为空");
        }

        if(documentId == null || documentId <= 0){
            throw new IllegalArgumentException("文档Id必须大于0");
        }

        if(chunkIndex < 1){
            throw new IllegalArgumentException("切片索引必须从1开始");
        }

        if(embeddingVersion == null || embeddingVersion.isBlank()){
            throw new IllegalArgumentException("版本不能为空");
        }

        if(values == null){
            throw new IllegalArgumentException("向量坐标不能为空");
        }

        if(values.isEmpty()){
            throw new IllegalArgumentException("向量集合不能为空集合");
        }

        for(int index = 0;index <values.size();index++){
            Float value = values.get(index);

            if(value == null || !Float.isFinite(value)){
                throw new IllegalArgumentException(
                        "待写入向量包含非法数值，维度索引：" + index
                );
            }
        }

        values = List.copyOf(values);
    }
}
