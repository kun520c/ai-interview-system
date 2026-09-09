package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FollowUpTargetResolver {

    private static final int MAX_TARGET_POINT_COUNT = 2;

    public List<Long> resolve(
            EvaluationContext context,
            List<LlmEvaluationSuggestion.ScoringPointResult> scoringPointResults
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "EvaluationContext不能为空"
            );
        }

        if (scoringPointResults == null) {
            throw new IllegalArgumentException(
                    "scoringPointResults不能为空"
            );
        }

        for (int index = 0;index < scoringPointResults.size();index++) {
            LlmEvaluationSuggestion.ScoringPointResult result =
                    scoringPointResults.get(index);

            if (result == null) {
                throw new IllegalArgumentException(
                        "scoringPointResults包含空结果，索引："
                                + index
                );
            }

            if (result.scoringPointId() == null) {
                throw new IllegalArgumentException(
                        "scoringPointResult.scoringPointId不能为null"
                );
            }

            if (result.covered() == null) {
                throw new IllegalArgumentException(
                        "scoringPointResult.covered不能为null"
                );
            }
        }

        Set<Long> uncoveredPointIds =
                scoringPointResults.stream()
                        .filter(
                                result ->
                                        Boolean.FALSE.equals(
                                                result.covered()
                                        )
                        )
                        .map(
                                LlmEvaluationSuggestion
                                        .ScoringPointResult
                                        ::scoringPointId
                        )
                        .collect(Collectors.toSet());

        List<Long> targets = new ArrayList<>();

        context.scoringPoints()
                .stream()
                .filter(
                        point ->
                                point.pointType()
                                            == QuestionPointType.CORE
                )
                .filter(
                        point ->
                                uncoveredPointIds.contains(
                                        point.scoringPointId()
                                )
                )
                .map(ScoringPointSnapshot::scoringPointId)
                .limit(MAX_TARGET_POINT_COUNT)
                .forEach(targets::add);

        if (targets.size() < MAX_TARGET_POINT_COUNT) {
            context.scoringPoints()
                    .stream()
                    .filter(
                            point ->
                                        point.pointType()
                                            != QuestionPointType.CORE
                    )
                    .filter(
                            point -> uncoveredPointIds.contains(
                                                        point.scoringPointId()
                            )
                    )
                    .map(ScoringPointSnapshot::scoringPointId)
                    .limit(
                            MAX_TARGET_POINT_COUNT
                                        - targets.size()
                    )
                    .forEach(targets::add);
        }

        return List.copyOf(targets);
    }
}