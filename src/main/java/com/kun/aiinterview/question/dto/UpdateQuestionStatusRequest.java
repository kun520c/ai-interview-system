package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.question.enums.QuestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateQuestionStatusRequest {
    @NotNull(message = "题目状态不能为空")
    private QuestionStatus status;
}
