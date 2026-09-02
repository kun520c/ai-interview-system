package com.kun.aiinterview.knowledge.retrieval;

import com.kun.aiinterview.knowledge.vector.VectorSearchHit;

import java.util.List;

public record RetrievalResult(
        String query,
        int requestedTopK,
        String embeddingModel,
        String embeddingVersion,
        List<VectorSearchHit> rawHits,
        List<RetrievedChunk> items
) {

    public RetrievalResult {
        rawHits = List.copyOf(rawHits);
        items = List.copyOf(items);
    }

    public RetrievalResult(
            String query,
            int requestedTopK,
            String embeddingModel,
            String embeddingVersion,
            List<RetrievedChunk> items
    ) {
        this(
                query,
                requestedTopK,
                embeddingModel,
                embeddingVersion,
                List.of(),
                items
        );
    }
}
