package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.question.enums.QuestionDifficulty;

public record InterviewSessionResponse(

        Long sessionId,

        QuestionDifficulty difficulty,

        InterviewSessionStatus status,

        Integer plannedQuestionCount,

        Integer completedQuestionCount,

        InterviewQuestionResponse currentQuestion
) {
}
