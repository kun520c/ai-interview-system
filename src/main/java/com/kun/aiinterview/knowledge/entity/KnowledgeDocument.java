package com.kun.aiinterview.knowledge.entity;

import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KnowledgeDocument {
    private Long id;
    private String title;
    private KnowledgeCategory category;
    private String fileName;
    private KnowledgeFileType fileType;
    private String content;
    private String contentHash;
    private String source;
    private Integer documentVersion;
    private KnowledgeProcessingStatus processingStatus;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
