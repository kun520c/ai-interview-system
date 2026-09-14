package com.kun.aiinterview.interview.evaluation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kun.aiinterview.question.enums.QuestionPointType;

public record ScoringPointSnapshot(
        @JsonProperty("id")
        @JsonAlias("scoringPointId")
        Long scoringPointId,
        QuestionPointType pointType,
        String content,
        int weight
) {
}
