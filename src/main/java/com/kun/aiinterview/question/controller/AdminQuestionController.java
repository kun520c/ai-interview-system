package com.kun.aiinterview.question.controller;

import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.question.vo.CreateQuestionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {
    private final QuestionAdminService questionAdminService;

    @PostMapping
    public Result<CreateQuestionResponse> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request
            ){
        Long questionId = questionAdminService.createQuestion(request);
        CreateQuestionResponse response = CreateQuestionResponse.builder()
                .questionId(questionId)
                .build();

        return Result.success(response);
    }

    @PutMapping("/{questionId}")
    public Result<Void> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody UpdateQuestionRequest request
    ) {
        questionAdminService.updateQuestion(questionId, request);
        return Result.success();
    }
}
