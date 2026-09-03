package com.kun.aiinterview.interview.evaluation.standard;

import org.springframework.stereotype.Component;

@Component
public class EvaluationStandard {

    public static final String VERSION =
                "evaluation-standard-v1";

    public EvaluationLevel resolveLevel(
            int totalScore
    ){
        if(totalScore < 0 || totalScore > 100){
            throw new IllegalArgumentException(
                    "totalScore必须在0到100之间"
            );
        }

        if(totalScore >= 90){
            return EvaluationLevel.EXCELLENT;
        }

        if(totalScore >= 80){
            return EvaluationLevel.GOOD;
        }

        if(totalScore >= 60){
            return EvaluationLevel.FAIR;
        }

        return EvaluationLevel.WEAK;
    }
}
