package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.question.enums.QuestionCategory;

public record InterviewDetailQuestionResponse(

        Long interviewQuestionId,

        InterviewQuestionType questionType,

        QuestionCategory category,

        String knowledgePoint,

        String questionContent,

        Integer planOrder,

        Integer displayOrder,

        InterviewQuestionStatus status,

        InterviewDetailAnswerResponse answer,

        InterviewEvaluationResponse evaluation

) {
}
