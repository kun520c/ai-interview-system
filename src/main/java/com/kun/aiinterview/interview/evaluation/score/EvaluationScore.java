package com.kun.aiinterview.interview.evaluation.score;

public record EvaluationScore(
        int correctnessScore,
        int completenessScore,
        int depthScore,
        int clarityScore,
        int practiceScore,
        int totalScore
) {
}
