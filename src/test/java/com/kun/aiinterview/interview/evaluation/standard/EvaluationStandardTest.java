package com.kun.aiinterview.interview.evaluation.standard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationStandardTest {

    private final EvaluationStandard evaluationStandard =
            new EvaluationStandard();

    @ParameterizedTest
    @CsvSource({
            "0, WEAK",
            "1, WEAK",
            "59, WEAK",
            "60, FAIR",
            "79, FAIR",
            "80, GOOD",
            "89, GOOD",
            "90, EXCELLENT",
            "99, EXCELLENT",
            "100, EXCELLENT"
    })
    void shouldResolveLevelFromTotalScore(
            int totalScore,
            EvaluationLevel expectedLevel
    ) {
        assertEquals(
                expectedLevel,
                evaluationStandard.resolveLevel(totalScore)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void shouldRejectScoreOutsideSupportedRange(int totalScore) {
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluationStandard.resolveLevel(totalScore)
        );
    }

    @Test
    void shouldExposeFixedEvaluationStandardVersion() {
        assertEquals(
                "evaluation-standard-v1",
                EvaluationStandard.VERSION
        );
    }

    @Test
    void shouldDefineOnlySupportedEvaluationLevelsInExpectedOrder() {
        assertArrayEquals(
                new EvaluationLevel[]{
                        EvaluationLevel.EXCELLENT,
                        EvaluationLevel.GOOD,
                        EvaluationLevel.FAIR,
                        EvaluationLevel.WEAK
                },
                EvaluationLevel.values()
        );
    }
}
