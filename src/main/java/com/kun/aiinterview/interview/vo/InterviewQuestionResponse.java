package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.question.enums.QuestionCategory;

public record InterviewQuestionResponse(

        Long interviewQuestionId,

        QuestionCategory category,

        String knowledgePoint,

        InterviewQuestionType questionType,

        String content

) {
}
