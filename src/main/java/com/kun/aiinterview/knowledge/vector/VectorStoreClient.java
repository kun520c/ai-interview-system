package com.kun.aiinterview.knowledge.vector;

import java.util.List;

public interface VectorStoreClient {

    void insert(List<VectorWriteItem> items);

    void deleteByVectorIds(List<String> vectorIds);

    void deleteByDocumentId(long documentId);

    List<VectorSearchHit> search(
            List<Float> queryVector,
            String embeddingVersion,
            int topK
    );
}
