package com.kun.aiinterview.knowledge.controller;

import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.knowledge.dto.UploadKnowledgeDocumentRequest;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentAdminService;
import com.kun.aiinterview.knowledge.vo.UploadKnowledgeDocumentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge/documents")
@RequiredArgsConstructor
public class AdminKnowledgeDocumentController {

    private final KnowledgeDocumentAdminService knowledgeDocumentAdminService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadKnowledgeDocumentResponse> uploadDocument(
            @Valid @ModelAttribute
            UploadKnowledgeDocumentRequest request
    ) {
        UploadKnowledgeDocumentResponse response =
                knowledgeDocumentAdminService.uploadDocument(request);

        return Result.success(response);
    }
}
