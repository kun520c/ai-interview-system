package com.kun.aiinterview.interview.orchestration;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;

import java.util.List;

public record EvaluationOrchestrationResult(

        Long evaluationId,

        Long answerId,

        Long mainInterviewQuestionId,

        EvaluationPhase evaluationPhase,

        DecisionAction decisionAction,

        int totalScore,

        EvaluationLevel level,

        boolean followUpRecommended,

        String suggestedFollowUp,

        List<Long> followUpTargetPointIds,

        String retrievalBatchId
) {
    public EvaluationOrchestrationResult{
        if (followUpTargetPointIds == null) {
            throw new IllegalArgumentException(
                    "followUpTargetPointIds不能为null"
            );
        }

        followUpTargetPointIds =
                List.copyOf(followUpTargetPointIds);
    }
}
