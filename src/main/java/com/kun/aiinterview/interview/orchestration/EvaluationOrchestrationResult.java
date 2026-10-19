package com.kun.aiinterview.interview.orchestration;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
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

        String retrievalBatchId,

        AnswerEvaluation evaluation,

        EvaluationRetrievalResult pendingTrace
) {
    // Historical evaluations have no pending trace to insert.
    public EvaluationOrchestrationResult(Long evaluationId, Long answerId, Long mainInterviewQuestionId,
            EvaluationPhase evaluationPhase, DecisionAction decisionAction, int totalScore,
            EvaluationLevel level, boolean followUpRecommended, String suggestedFollowUp,
            List<Long> followUpTargetPointIds, String retrievalBatchId, AnswerEvaluation evaluation) {
        this(evaluationId, answerId, mainInterviewQuestionId, evaluationPhase, decisionAction,
                totalScore, level, followUpRecommended, suggestedFollowUp,
                followUpTargetPointIds, retrievalBatchId, evaluation, null);
    }

    public EvaluationOrchestrationResult withPendingTrace(EvaluationRetrievalResult trace) {
        return new EvaluationOrchestrationResult(evaluationId, answerId, mainInterviewQuestionId,
                evaluationPhase, decisionAction, totalScore, level, followUpRecommended,
                suggestedFollowUp, followUpTargetPointIds, retrievalBatchId, evaluation, trace);
    }

    public EvaluationOrchestrationResult withPersistedId() {
        if (evaluation == null) {
            return this;
        }
        return new EvaluationOrchestrationResult(evaluation.getId(), answerId, mainInterviewQuestionId,
                evaluationPhase, decisionAction, totalScore, level, followUpRecommended,
                suggestedFollowUp, followUpTargetPointIds, retrievalBatchId, evaluation, null);
    }

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
