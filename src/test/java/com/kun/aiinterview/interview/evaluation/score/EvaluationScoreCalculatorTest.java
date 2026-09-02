package com.kun.aiinterview.interview.evaluation.score;

import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationScoreCalculatorTest {

    private final EvaluationScoreCalculator calculator =
            new EvaluationScoreCalculator();

    @Test
    void givenValidatedEvaluation_whenCalculate_thenPreservesDimensionsAndCalculatesTotal() {
        ValidatedEvaluationSuggestion evaluation = validatedEvaluation(
                18,
                16,
                14,
                12,
                10
        );

        EvaluationScore result = calculator.calculate(evaluation);

        assertThat(result).isEqualTo(new EvaluationScore(
                18,
                16,
                14,
                12,
                10,
                70
        ));
    }

    @Test
    void givenAllZeroDimensions_whenCalculate_thenTotalIsZero() {
        EvaluationScore result = calculator.calculate(
                validatedEvaluation(0, 0, 0, 0, 0)
        );

        assertThat(result).isEqualTo(new EvaluationScore(0, 0, 0, 0, 0, 0));
    }

    @Test
    void givenAllMaximumDimensions_whenCalculate_thenTotalIsOneHundred() {
        EvaluationScore result = calculator.calculate(
                validatedEvaluation(20, 20, 20, 20, 20)
        );

        assertThat(result).isEqualTo(new EvaluationScore(
                20,
                20,
                20,
                20,
                20,
                100
        ));
    }

    @Test
    void givenNullValidatedEvaluation_whenCalculate_thenRejects() {
        assertThatThrownBy(() -> calculator.calculate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已校验评价结果");
    }

    private ValidatedEvaluationSuggestion validatedEvaluation(
            int correctnessScore,
            int completenessScore,
            int depthScore,
            int clarityScore,
            int practiceScore
    ) {
        return new ValidatedEvaluationSuggestion(
                correctnessScore,
                completenessScore,
                depthScore,
                clarityScore,
                practiceScore,
                List.of("优点"),
                List.of("遗漏点"),
                "修正建议",
                List.of(),
                false,
                null
        );
    }
}
