package com.kun.aiinterview.knowledge.dto;

import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UploadKnowledgeDocumentRequest {
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过200个字符")
    private String title;

    @NotNull(message = "知识领域不能为空")
    private KnowledgeCategory category;

    @Size(max = 255, message = "文档来源长度不能超过255个字符")
    private String source;
}
