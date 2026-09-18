package com.kun.aiinterview.user.service;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.mapper.UserWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserWeaknessService {

    private final UserWeaknessMapper userWeaknessMapper;
    private final EvaluationStandard evaluationStandard;

    public void applySessionEvaluations(
            Long userId,
            List<AnswerEvaluation> evaluations
    ) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId不能为空"
            );
        }
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalArgumentException(
                    "evaluations不能为空"
            );
        }

        Map<WeaknessKey, List<Integer>> grouped = new LinkedHashMap<>();
        for (AnswerEvaluation evaluation : evaluations) {
            validateEvaluation(evaluation);
            WeaknessKey key = new WeaknessKey(
                    evaluation.getCategory(),
                    evaluation.getKnowledgePoint()
            );
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(evaluation.getTotalScore());
        }

        for (Map.Entry<WeaknessKey, List<Integer>> entry
                : grouped.entrySet()) {
            BigDecimal averageScore =
                    calculateAverageScore(entry.getValue());
            BigDecimal weaknessScore = BigDecimal.valueOf(100)
                    .subtract(averageScore);
            WeaknessKey key = entry.getKey();
            EvaluationLevel level =
                    evaluationStandard.resolveLevel(averageScore);
            if (level == EvaluationLevel.EXCELLENT
                    || level == EvaluationLevel.GOOD) {
                userWeaknessMapper.resolveActiveWeakness(
                        userId,
                        key.category(),
                        key.knowledgePoint(),
                        weaknessScore
                );
            } else {
                userWeaknessMapper.upsertDiscoveredWeakness(
                        userId,
                        key.category(),
                        key.knowledgePoint(),
                        weaknessScore
                );
            }
        }
    }

    private void validateEvaluation(AnswerEvaluation evaluation) {
        if (evaluation == null) {
            throw new IllegalStateException(
                    "Evaluation不能为空"
            );
        }
        if (evaluation.getCategory() == null) {
            throw new IllegalStateException(
                    "Evaluation缺少category"
            );
        }
        if (evaluation.getKnowledgePoint() == null
                || evaluation.getKnowledgePoint().isBlank()) {
            throw new IllegalStateException(
                    "Evaluation缺少knowledgePoint"
            );
        }
        if (evaluation.getTotalScore() == null) {
            throw new IllegalStateException(
                    "Evaluation缺少totalScore"
            );
        }
        if (evaluation.getTotalScore() < 0
                || evaluation.getTotalScore() > 100) {
            throw new IllegalStateException(
                    "Evaluation的totalScore必须在0到100之间"
            );
        }
    }

    private BigDecimal calculateAverageScore(List<Integer> scores) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Integer score : scores) {
            sum = sum.add(BigDecimal.valueOf(score));
        }

        return sum.divide(
                BigDecimal.valueOf(scores.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private record WeaknessKey(
            QuestionCategory category,
            String knowledgePoint
    ) {
    }
}
