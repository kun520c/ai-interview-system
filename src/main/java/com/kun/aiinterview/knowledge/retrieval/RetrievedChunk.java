package com.kun.aiinterview.knowledge.retrieval;

import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;

public record RetrievedChunk(
        int vectorRank,
        double similarityScore,

        Long chunkId,
        Long documentId,
        Integer documentVersion,
        Integer chunkIndex,

        String vectorId,

        String title,
        KnowledgeCategory category,
        String source,

        String content
) {
}
