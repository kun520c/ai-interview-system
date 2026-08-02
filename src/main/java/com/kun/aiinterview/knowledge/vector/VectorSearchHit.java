package com.kun.aiinterview.knowledge.vector;

public record VectorSearchHit(
        String vectorId,
        Long documentId,
        int chunkIndex,
        String embeddingVersion,
        double similarityScore
) {
    public VectorSearchHit{
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

        if(!Double.isFinite(similarityScore)){
            throw new IllegalArgumentException("向量相似度分数必须是有限数字");
        }
    }
}
