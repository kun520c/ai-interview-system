package com.kun.aiinterview.interview.evaluation.validation;

import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;

import java.util.List;

public record ValidatedEvaluationSuggestion (
        int correctnessScore,
        int completenessScore,
        int depthScore,
        int clarityScore,
        int practiceScore,

        List<String> strengths,
        List<String> missingPoints,
        String correction,

        List<LlmEvaluationSuggestion.ScoringPointResult> scoringPointResults,

        boolean followUpRecommended,
        String suggestedFollowUp
){

    public ValidatedEvaluationSuggestion{
        strengths = List.copyOf(strengths);
        missingPoints = List.copyOf(missingPoints);
        scoringPointResults = List.copyOf(scoringPointResults);
    }
}
