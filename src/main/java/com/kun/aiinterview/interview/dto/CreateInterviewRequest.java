package com.kun.aiinterview.interview.dto;

import com.kun.aiinterview.question.enums.QuestionDifficulty;
import jakarta.validation.constraints.NotNull;

public record CreateInterviewRequest(

        @NotNull(message = "difficulty不能为空")
        QuestionDifficulty difficulty

) {
}
