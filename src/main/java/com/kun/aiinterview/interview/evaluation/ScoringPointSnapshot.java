package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.question.enums.QuestionPointType;

public record ScoringPointSnapshot(
        Long scoringPointId,
        QuestionPointType pointType,
        String content,
        int weight
) {
}
