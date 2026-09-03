package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScore;
import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FollowUpPolicy {

    private static final int WEAK_SCORE_THRESHOLD = 60;

    public EvaluationDecision decide(
            EvaluationContext context,
            ValidatedEvaluationSuggestion suggestion,
            EvaluationScore score,
            boolean hasNextMainQuestion
    ){

        if(context == null){
            throw new IllegalArgumentException(
                    "EvaluationContext不能为空"
            );
        }

        if(suggestion == null){
            throw new IllegalArgumentException(
                    "ValidatedEvaluationSuggestion不能为空"
            );
        }

        if(score == null){
            throw new IllegalArgumentException(
                    "EvaluationScore不能为空"
            );
        }

        if(context.mode()
                == EvaluationMode.FOLLOW_UP_ANSWER){

            return finalDecision(
                    hasNextMainQuestion
            );
        }

        if(shouldFollowUp(
                context,
                suggestion,
                score
        )){
            return new EvaluationDecision(
                    EvaluationPhase.INITIAL,
                    DecisionAction.FOLLOW_UP
            );
        }

        return finalDecision(
                hasNextMainQuestion
        );
    }

    private boolean shouldFollowUp(
            EvaluationContext context,
            ValidatedEvaluationSuggestion suggestion,
            EvaluationScore score
    ){
        if(!suggestion.followUpRecommended()){
            return false;
        }

        return score.totalScore()
                < WEAK_SCORE_THRESHOLD
                || hasUncoveredCorePoint(
                        context,
                        suggestion
        );
    }

    private boolean hasUncoveredCorePoint(
            EvaluationContext context,
            ValidatedEvaluationSuggestion suggestion
    ){
        Set<Long> uncoveredPointIds =
                suggestion
                        .scoringPointResults()
                        .stream()
                        .filter(
                                result ->
                                        !result.covered()
                        )
                        .map(
                                LlmEvaluationSuggestion
                                        .ScoringPointResult
                                        ::scoringPointId
                        )
                        .collect(
                                Collectors.toSet()
                        );

        return context
                .scoringPoints()
                .stream()
                .filter(
                        point ->
                                                    point.pointType()
                                                            == QuestionPointType.CORE
                )
                .map(
                        ScoringPointSnapshot::scoringPointId
                )
                .anyMatch(
                        uncoveredPointIds::contains
                );
    }

    private EvaluationDecision finalDecision(
            boolean hasNextMainQuestion
    ){

        return new EvaluationDecision(
                EvaluationPhase.FINAL,
                hasNextMainQuestion
                        ? DecisionAction.NEXT_MAIN
                        : DecisionAction.FINISH
        );
    }
}
