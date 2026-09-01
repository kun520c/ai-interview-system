package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.question.enums.QuestionCategory;

import java.util.List;

public record EvaluationContext(
        Long currentAnswerId,
        Long currentInterviewQuestionId,

        Long mainAnswerId,
        Long mainInterviewQuestionId,

        EvaluationMode mode,

        QuestionCategory category,
        String knowledgePoint,

        String mainQuestionContent,
        String referenceAnswerSnapshot,

        List<ScoringPointSnapshot> scoringPoints,

        String mainAnswerContent,

        String followUpQuestionContent,
        String followUpAnswerContent,

        List<Long> followUpTargetPointIds
) {

    public EvaluationContext{
        scoringPoints = List.copyOf(scoringPoints);
        followUpTargetPointIds = List.copyOf(followUpTargetPointIds);
    }
}
