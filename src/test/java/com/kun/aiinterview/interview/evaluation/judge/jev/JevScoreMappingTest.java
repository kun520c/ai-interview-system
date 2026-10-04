package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class JevScoreMappingTest {
    @ParameterizedTest
    @CsvSource({"0,0", "4,20", "1,5", "2,10", "3,15", "0.099,0", "0.1,1",
            "3.899,19", "3.9,20", "2.5,13"})
    void mapsEndpointsAndHalfUpRounding(double score, int expected) {
        assertThat(JevJudgeProvider.mapScore(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {-0.001, 4.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidScoresInsteadOfClamping(Double score) {
        assertThatThrownBy(() -> JevJudgeProvider.mapScore(score)).isInstanceOf(ExternalServiceException.class);
    }
}
