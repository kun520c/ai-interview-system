package com.kun.aiinterview.interview.evaluation.score;

import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import org.springframework.stereotype.Component;

@Component
public class EvaluationScoreCalculator {

    public EvaluationScore calculate(
            ValidatedEvaluationSuggestion evaluation
    ) {

        if (evaluation == null) {
            throw new IllegalArgumentException(
                    "已校验评价结果不能为空"
            );
        }

        int totalScore =
                evaluation.correctnessScore()
                    + evaluation.completenessScore()
                    + evaluation.depthScore()
                    + evaluation.clarityScore()
                    + evaluation.practiceScore();

        return new EvaluationScore(
                evaluation.correctnessScore(),
                evaluation.completenessScore(),
                evaluation.depthScore(),
                evaluation.clarityScore(),
                evaluation.practiceScore(),
                totalScore
        );
    }
}
