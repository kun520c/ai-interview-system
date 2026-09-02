package com.kun.aiinterview.interview.evaluation;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class EvaluationRagQueryBuilder {

    public String build(EvaluationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("EvaluationContext不能为空");
        }

        if (context.mode() == null) {
            throw new IllegalStateException("EvaluationMode不能为空");
        }

        return switch (context.mode()) {
            case MAIN_ANSWER -> buildMainQuery(context);
            case FOLLOW_UP_ANSWER -> buildFollowUpQuery(context);
        };
    }

    private String buildMainQuery(EvaluationContext context) {
        StringBuilder query = new StringBuilder();

        query.append("主问题：")
                .append(context.mainQuestionContent().strip())
                .append("\n评分点：");

        for (ScoringPointSnapshot scoringPoint : context.scoringPoints()) {
            query.append("\n- ")
                    .append(scoringPoint.content().strip());
        }

        return query.toString();
    }

    private String buildFollowUpQuery(EvaluationContext context) {
        StringBuilder query = new StringBuilder();

        query.append("主问题：")
                .append(context.mainQuestionContent().strip())
                .append("\n追问：")
                .append(context.followUpQuestionContent().strip())
                .append("\n追问目标评分点：");

        for (Long targetPointId : context.followUpTargetPointIds()) {
            ScoringPointSnapshot scoringPoint =
                    findScoringPoint(context, targetPointId);

            query.append("\n- ")
                    .append(scoringPoint.content().strip());
        }

        return query.toString();
    }

    private ScoringPointSnapshot findScoringPoint(
            EvaluationContext context,
            Long targetPointId
    ) {
        return context.scoringPoints().stream()
                .filter(scoringPoint ->
                        Objects.equals(
                                scoringPoint.scoringPointId(),
                                targetPointId
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "追问目标评分点不存在于MAIN评分标准中"
                        )
                );
    }
}
