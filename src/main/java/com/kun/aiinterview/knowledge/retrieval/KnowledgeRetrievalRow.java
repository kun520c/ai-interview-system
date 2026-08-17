package com.kun.aiinterview.knowledge.retrieval;

import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import lombok.Data;

@Data
public class KnowledgeRetrievalRow {

    private Long chunkId;

    private Long documentId;

    private Integer chunkIndex;

    private Integer documentVersion;

    private String vectorId;

    private String content;

    private String title;

    private KnowledgeCategory category;

    private String source;
}
