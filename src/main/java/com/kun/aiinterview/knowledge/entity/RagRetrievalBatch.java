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
public class RagRetrievalBatch {

    private String retrievalBatchId;

    private Long answerId;

    private String queryText;

    private Integer topK;

    private String filterSnapshot;

    private String embeddingModel;

    private String embeddingVersion;

    private Integer rawHitCount;

    private Integer evidenceHitCount;

    private LocalDateTime createdAt;
}
