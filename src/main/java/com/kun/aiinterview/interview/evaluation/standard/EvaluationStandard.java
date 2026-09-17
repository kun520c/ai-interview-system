package com.kun.aiinterview.interview.evaluation.standard;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class EvaluationStandard {

    public static final String VERSION =
                "evaluation-standard-v1";

    public EvaluationLevel resolveLevel(
            int totalScore
    ) {
        return resolveLevel(
                BigDecimal.valueOf(totalScore)
        );
    }

    public EvaluationLevel resolveLevel(
            BigDecimal totalScore
    ) {
        if (totalScore == null) {
            throw new IllegalArgumentException(
                    "totalScore不能为空"
            );
        }

        if (totalScore.compareTo(BigDecimal.ZERO) < 0
                || totalScore.compareTo(
                        BigDecimal.valueOf(100)
                ) > 0) {
            throw new IllegalArgumentException(
                    "totalScore必须在0到100之间"
            );
        }

        if (totalScore.compareTo(
                BigDecimal.valueOf(90)
        ) >= 0) {
            return EvaluationLevel.EXCELLENT;
        }

        if (totalScore.compareTo(
                BigDecimal.valueOf(80)
        ) >= 0) {
            return EvaluationLevel.GOOD;
        }

        if (totalScore.compareTo(
                BigDecimal.valueOf(60)
        ) >= 0) {
            return EvaluationLevel.FAIR;
        }

        return EvaluationLevel.WEAK;
    }
}
