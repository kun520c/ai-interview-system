package com.kun.aiinterview.interview.dto;

import com.kun.aiinterview.common.validation.Utf8ByteSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitInterviewAnswerRequest(

        @NotNull(message = "interviewQuestionId不能为空")
        @Positive(message = "interviewQuestionId必须大于0")
        Long interviewQuestionId,

        @NotBlank(message = "answerContent不能为空")
        @Size(max = Utf8ByteSize.MYSQL_TEXT_MAX_BYTES,
                message = "answerContent长度不能超过65535位")
        @Utf8ByteSize(message = "answerContent不能超过65535个UTF-8字节")
        String answerContent,

        @NotBlank(message = "requestId不能为空")
        @Size(max = 64, message = "requestId长度不能超过64位")
        String requestId

) {
}
