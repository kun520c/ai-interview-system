package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;

import java.util.List;

public record EvaluationRetrievalResult(
        String query,
        int requestedTopK,
        String embeddingModel,
        String embeddingVersion,
        List<VectorSearchHit> rawHits,
        List<RetrievedChunk> evidence
) {

    public EvaluationRetrievalResult {
        rawHits = List.copyOf(rawHits);
        evidence = List.copyOf(evidence);
    }
}
