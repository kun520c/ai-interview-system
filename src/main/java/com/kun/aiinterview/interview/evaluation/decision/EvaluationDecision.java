package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;

import java.util.List;

public record EvaluationDecision(
        EvaluationPhase evaluationPhase,
        DecisionAction decisionAction,
        List<Long> followUpTargetPointIds
) {

    public EvaluationDecision{

        if (evaluationPhase == null) {
            throw new IllegalArgumentException(
                    "evaluationPhase不能为空"
            );
        }

        if (decisionAction == null) {
            throw new IllegalArgumentException(
                    "decisionAction不能为空"
            );
        }

        if (followUpTargetPointIds == null) {
            throw new IllegalArgumentException(
                    "followUpTargetPointIds不能为null"
            );
        }

        followUpTargetPointIds =
                List.copyOf(followUpTargetPointIds);

        if (evaluationPhase == EvaluationPhase.INITIAL && decisionAction != DecisionAction.FOLLOW_UP) {
            throw new IllegalArgumentException(
                    "INITIAL评价必须进入FOLLOW_UP"
            );
        }

        if (evaluationPhase == EvaluationPhase.FINAL && decisionAction == DecisionAction.FOLLOW_UP) {
            throw new IllegalArgumentException(
                    "FINAL评价不能进入FOLLOW_UP"
            );
        }

        if (decisionAction == DecisionAction.FOLLOW_UP) {
            if (followUpTargetPointIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "FOLLOW_UP必须包含目标评分点"
                );
            }

            if (followUpTargetPointIds.size() > 2) {
                throw new IllegalArgumentException(
                        "FOLLOW_UP目标评分点最多两个"
                );
            }
        } else if (!followUpTargetPointIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "非FOLLOW_UP决策不能包含目标评分点"
            );
        }
    }
}
