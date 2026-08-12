package com.kun.aiinterview.knowledge.entity;

import com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    private Long id;

    private Long documentId;

    private Integer documentVersion;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private String vectorId;

    private String embeddingModel;

    private String embeddingVersion;

    private KnowledgeChunkStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
