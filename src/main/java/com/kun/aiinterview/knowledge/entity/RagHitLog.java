package com.kun.aiinterview.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHitLog {

    private Long id;

    private Long answerId;

    private String retrievalBatchId;

    private Long chunkId;

    private Double similarityScore;

    private Integer rankNo;

    private String queryText;

    private Integer topK;

    private String filterSnapshot;

    private String embeddingModel;

    private String embeddingVersion;

    private LocalDateTime createdAt;
}
