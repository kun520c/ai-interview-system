package com.kun.aiinterview.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitInterviewAnswerRequest(

        @NotNull(message = "interviewQuestionId不能为空")
        @Positive(message = "interviewQuestionId必须大于0")
        Long interviewQuestionId,

        @NotBlank(message = "answerContent不能为空")
        String answerContent,

        @NotBlank(message = "requestId不能为空")
        @Size(max = 64, message = "requestId长度不能超过64位")
        String requestId

) {
}
