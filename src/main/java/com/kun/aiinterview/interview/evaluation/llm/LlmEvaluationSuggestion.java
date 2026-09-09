package com.kun.aiinterview.interview.evaluation.llm;

import java.util.List;

public record LlmEvaluationSuggestion(

        Integer correctnessScore,
        Integer completenessScore,
        Integer depthScore,
        Integer clarityScore,
        Integer practiceScore,

        List<String> strengths,
        List<String> missingPoints,

        String correction,

        List<ScoringPointResult> scoringPointResults,

        Boolean followUpRecommended,
        String suggestedFollowUp
) {

    public record ScoringPointResult(

            Long scoringPointId,

            Boolean covered,

            String evidence
    ) {
    }
}
