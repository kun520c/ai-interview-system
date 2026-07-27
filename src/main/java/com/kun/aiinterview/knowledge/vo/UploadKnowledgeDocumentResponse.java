package com.kun.aiinterview.knowledge.vo;

import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class UploadKnowledgeDocumentResponse {
    private Long documentId;
    private String fileName;
    private KnowledgeFileType fileType;
    private Integer documentVersion;
    private KnowledgeProcessingStatus processingStatus;
}
