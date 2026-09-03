package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;

public record EvaluationDecision(
        EvaluationPhase evaluationPhase,
        DecisionAction decisionAction
) {

    public EvaluationDecision{

        if(evaluationPhase == null){
            throw new IllegalArgumentException(
                    "evaluationPhase不能为空"
            );
        }

        if(decisionAction == null){
            throw new IllegalArgumentException(
                    "decisionAction不能为空"
            );
        }

        if(evaluationPhase == EvaluationPhase.INITIAL && decisionAction != DecisionAction.FOLLOW_UP){
            throw new IllegalArgumentException(
                    "INITIAL评价必须进入FOLLOW_UP"
            );
        }

        if(evaluationPhase == EvaluationPhase.FINAL && decisionAction == DecisionAction.FOLLOW_UP){
            throw new IllegalArgumentException(
                    "FINAL评价不能进入FOLLOW_UP"
            );
        }
    }
}
