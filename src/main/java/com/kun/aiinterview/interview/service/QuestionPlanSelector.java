package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

@Component
public class QuestionPlanSelector {

    public List<Question> select(
            List<Question> candidates,
            InterviewPlanConfig.InterviewPlanRule rule
    ) {
        return select(candidates, rule, ThreadLocalRandom.current()::nextInt);
    }

    List<Question> select(
            List<Question> candidates,
            InterviewPlanConfig.InterviewPlanRule rule,
            IntUnaryOperator randomIndex
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }

        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }

        if (randomIndex == null) {
            throw new IllegalArgumentException("randomIndex must not be null");
        }

        if (rule.plannedQuestionCount() != rule.requiredCategories().size()) {
            throw new IllegalArgumentException(
                    "interview plan must require exactly one question per category"
            );
        }

        Map<QuestionCategory, List<Question>> byCategory = groupCandidates(
                candidates,
                rule.requiredCategories()
        );
        List<Question> selected = new ArrayList<>(rule.plannedQuestionCount());
        for (QuestionCategory category : rule.requiredCategories()) {
            List<Question> bucket = byCategory.get(category);
            if (bucket.isEmpty()) {
                throw new IllegalStateException(
                        "no enabled question for required category: " + category
                );
            }

            // Stable indexing lets the same random sequence replay across query orders.
            bucket.sort(Comparator.comparing(Question::getId));
            selected.add(bucket.get(randomIndex.applyAsInt(bucket.size())));
        }

        return List.copyOf(selected);
    }

    private Map<QuestionCategory, List<Question>> groupCandidates(
            List<Question> candidates,
            List<QuestionCategory> requiredCategories
    ) {
        Map<QuestionCategory, List<Question>> byCategory = new EnumMap<>(QuestionCategory.class);
        for (QuestionCategory category : requiredCategories) {
            byCategory.put(category, new ArrayList<>());
        }

        Map<Long, Question> seenById = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            Question candidate = candidates.get(index);
            validateCandidate(candidate, index);
            Question previous = seenById.putIfAbsent(candidate.getId(), candidate);
            if (previous != null) {
                if (!previous.equals(candidate)) {
                    throw new IllegalArgumentException(
                            "conflicting candidates with id " + candidate.getId()
                    );
                }
                continue;
            }

            List<Question> bucket = byCategory.get(candidate.getCategory());
            if (bucket != null && candidate.getStatus() == QuestionStatus.ENABLED) {
                bucket.add(candidate);
            }
        }
        return byCategory;
    }

    private void validateCandidate(Question candidate, int index) {
        if (candidate == null) {
            throw new IllegalArgumentException(
                    "candidate must not be null at index " + index
            );
        }
        if (candidate.getId() == null || candidate.getId() <= 0) {
            throw new IllegalArgumentException(
                    "candidate.id must be positive at index " + index
            );
        }
        if (candidate.getCategory() == null) {
            throw new IllegalArgumentException(
                    "candidate.category must not be null at index " + index
            );
        }
    }
}
