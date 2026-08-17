package com.kun.aiinterview.knowledge.retrieval;

import java.util.List;

public record RetrievalResult(
        String query,
        int requestedTopK,
        String embeddingModel,
        String embeddingVersion,
        List<RetrievedChunk> items
) {

    public RetrievalResult{
        items = List.copyOf(items);
    }
}
