package com.kun.aiinterview.interview.evaluation.validation;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LlmEvaluationValidator {

    public ValidatedEvaluationSuggestion validate(
            EvaluationContext context,
            LlmEvaluationSuggestion suggestion
    ){

        if(context == null){
            throw new IllegalArgumentException(
                    "EvaluationContext不能为空"
            );
        }

        if(suggestion == null){
            throw new IllegalArgumentException(
                    "Llm评价结果不能为空"
            );
        }

        int correctnessScore =
                validateScore(
                        "correctnessScore",
                        suggestion.correctnessScore()
                );

        int completenessScore =
                validateScore(
                        "completenessScore",
                        suggestion.completenessScore()
                );

        int depthScore =
                validateScore(
                        "depthScore",
                        suggestion.depthScore()
                );

        int clarityScore =
                validateScore(
                        "clarityScore",
                        suggestion.clarityScore()
                );

        int practiceScore =
                validateScore(
                        "practiceScore",
                        suggestion.practiceScore()
                );

        List<String> strengths =
                validateTextList(
                        "strengths",
                        suggestion.strengths()
                );

        List<String> missingPoints =
                validateTextList(
                        "missingPoints",
                        suggestion.missingPoints()
                );

        List<LlmEvaluationSuggestion.ScoringPointResult>
                scoringPointResults =
                validateScoringPointResults(
                        context,
                        suggestion.scoringPointResults()
                );

        boolean followUpRecommended =
                validateFollowUpRecommendation(
                        context,
                        suggestion.followUpRecommended(),
                        suggestion.suggestedFollowUp()
                );

        return new ValidatedEvaluationSuggestion(
                correctnessScore,
                completenessScore,
                depthScore,
                clarityScore,
                practiceScore,

                strengths,
                missingPoints,
                normalizeNullableText(
                        suggestion.correction()
                ),

                scoringPointResults,

                followUpRecommended,
                normalizeNullableText(
                        suggestion.suggestedFollowUp()
                )
        );
    }

    private int validateScore(
            String fieldName,
            Integer score
    ){

        if(score == null){
            throw new IllegalStateException(
                    fieldName + "不能为空"
            );
        }

        if (score < 0 || score > 20) {
            throw new IllegalArgumentException(
                    fieldName + "必须在0到20之间"
            );
        }

        return score;
    }

    private List<String> validateTextList(
            String fieldName,
            List<String> values
    ){

        if(values == null){
            throw new IllegalStateException(
                    fieldName + "不能为空"
            );
        }

        for(int index = 0; index < values.size();index++){

            String value = values.get(index);

            if(value == null || value.isBlank()){
                throw new IllegalStateException(
                        fieldName
                            + "包含空白内容，索引："
                            + index
                );
            }
        }

        return List.copyOf(values);
    }

    private List<LlmEvaluationSuggestion.ScoringPointResult> validateScoringPointResults(
            EvaluationContext context,
            List<LlmEvaluationSuggestion.ScoringPointResult> results
    ){

        if(results == null){
            throw new IllegalStateException(
                    "scoringPointResults不能为空"
            );
        }

        Set<Long> expectedIds =
                context.scoringPoints()
                        .stream()
                        .map(
                                ScoringPointSnapshot::scoringPointId
                        )
                        .collect(Collectors.toSet());

        if(results.size() != expectedIds.size()){
            throw new IllegalStateException(
                    "scoringPointResults数量与MAIN评分点数量不一致"
            );
        }

        Set<Long> actualIds =
                new HashSet<>();

        for(int index = 0;index < results.size();index++){

            LlmEvaluationSuggestion.ScoringPointResult result =
                    results.get(index);

            if(result == null){
                throw new IllegalStateException(
                        "scoringPointResults包含空结果，索引："
                                + index
                );
            }

            Long scoringPointId =
                    result.scoringPointId();

            if(scoringPointId == null || scoringPointId <= 0){

                throw new IllegalStateException(
                        "scoringPointResult的scoringPointId非法"
                );
            }

            if(!expectedIds.contains(scoringPointId)){
                throw new IllegalStateException(
                        "LLM返回了不属于MAIN评分标准的评分点"
                );
            }

            if(!actualIds.add(scoringPointId)){
                throw new IllegalStateException(
                        "LLM返回了重复的评分点结果"
                );
            }

            if(result.covered() == null){
                throw new IllegalStateException(
                        "scoringPointResult.covered不能为空"
                );
            }

            if(result.covered()
                && (result.evidence() == null
                || result.evidence().isBlank())){

                throw new IllegalStateException(
                        "已覆盖评分点必须提供回答证据"
                );
            }
        }

        if(!actualIds.equals(expectedIds)){
            throw new IllegalStateException(
                    "LLM评分点结果未完整覆盖MAIN评分标准"
            );
        }

        return List.copyOf(results);
    }

    private boolean validateFollowUpRecommendation(
            EvaluationContext context,
            Boolean followUpRecommended,
            String suggestedFollowUp
    ){

        if(followUpRecommended == null){
            throw new IllegalStateException(
                    "followUpRecommended不能为空"
            );
        }

        if(context.mode()
                == EvaluationMode.FOLLOW_UP_ANSWER){

            if(followUpRecommended){
                throw new IllegalStateException(
                        "FOLLOW_UP回答不能再次建议追问"
                );
            }

            if(suggestedFollowUp != null
                && !suggestedFollowUp.isBlank()){

                throw new IllegalStateException(
                        "FOLLOW_UP回答不能返回新的候选追问"
                );
            }

            return false;
        }

        if(followUpRecommended){

            if(suggestedFollowUp == null
                    ||suggestedFollowUp.isBlank()){

                throw new IllegalStateException(
                        "建议追问时必须提供候选追问问题"
                );
            }

            return true;
        }

        if(suggestedFollowUp != null
            && !suggestedFollowUp.isBlank()){

            throw new IllegalStateException(
                    "为建议追问时不能提供候选追问问题"
            );
        }

        return false;
    }

    private String normalizeNullableText(
            String value
    ){

        if(value == null || value.isBlank()){
            return null;
        }

        return value.strip();
    }
}
