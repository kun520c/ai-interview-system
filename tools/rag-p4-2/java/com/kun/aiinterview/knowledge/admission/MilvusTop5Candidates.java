package com.kun.aiinterview.knowledge.admission;

import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.*;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import java.util.*;

/** Same business ports and eligibility SQL; no COSINE cutoff before semantic ranking. No writes. */
public final class MilvusTop5Candidates {
    private final EmbeddingClient embeddings; private final VectorStoreClient vectors; private final KnowledgeChunkMapper chunks;
    public MilvusTop5Candidates(EmbeddingClient embeddings, VectorStoreClient vectors, KnowledgeChunkMapper chunks) {
        this.embeddings = embeddings; this.vectors = vectors; this.chunks = chunks;
    }
    public RetrievalResult retrieve(String question) {
        if (question == null || question.isBlank() || question.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 3000) throw new IllegalArgumentException("QUESTION_SIZE");
        var embedding = embeddings.embed(List.of(question));
        if (embedding == null || embedding.vectors().size() != 1 || embedding.vectors().getFirst().inputIndex() != 0) throw new IllegalStateException("EMBEDDING_SHAPE");
        var hits = vectors.search(embedding.vectors().getFirst().values(), embedding.profileVersion(), 5);
        if (hits == null || hits.size() > 5) throw new IllegalStateException("TOP5_SHAPE");
        List<String> ids = hits.stream().map(h -> h.vectorId()).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new IllegalStateException("DUPLICATE_VECTOR");
        if (hits.isEmpty()) return new RetrievalResult(question, 5, embedding.model(), embedding.profileVersion(), hits, List.of());
        Map<String, KnowledgeRetrievalRow> eligible = new HashMap<>();
        for (var row : chunks.selectRetrievableByVectorIds(ids, embedding.model(), embedding.profileVersion())) {
            if (row == null || eligible.put(row.getVectorId(), row) != null) throw new IllegalStateException("ELIGIBLE_ROW_SHAPE");
        }
        List<RetrievedChunk> candidates = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            var hit = hits.get(index); var row = eligible.get(hit.vectorId()); if (row == null) continue;
            if (!Objects.equals(row.getDocumentId(), hit.documentId()) || !Objects.equals(row.getChunkIndex(), hit.chunkIndex())) throw new IllegalStateException("VECTOR_SQL_IDENTITY");
            if (row.getContent() == null || row.getContent().isBlank() || row.getContent().length() > 1200 || row.getTitle() == null || row.getTitle().length() > 256)
                throw new IllegalStateException("CANDIDATE_SIZE");
            candidates.add(new RetrievedChunk(index + 1, hit.similarityScore(), row.getChunkId(), row.getDocumentId(), row.getDocumentVersion(), row.getChunkIndex(),
                    row.getVectorId(), row.getTitle(), row.getCategory(), row.getSource(), row.getContent()));
        }
        return new RetrievalResult(question, 5, embedding.model(), embedding.profileVersion(), hits, candidates);
    }
}
