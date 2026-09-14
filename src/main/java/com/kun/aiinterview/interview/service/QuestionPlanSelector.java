package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class QuestionPlanSelector {

    public List<Question> select(
            List<Question> candidates,
            InterviewPlanConfig.InterviewPlanRule rule
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }

        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }

        validateCandidates(candidates);

        if (candidates.size() < rule.plannedQuestionCount()) {
            throw new IllegalStateException(
                    "not enough enabled questions for interview plan"
            );
        }

        List<Question> selected = new ArrayList<>();
        Set<Long> selectedQuestionIds = new LinkedHashSet<>();

        for (QuestionCategory category : rule.requiredCategories()) {
            Question question = findFirstUnselectedByCategory(
                    candidates,
                    category,
                    selectedQuestionIds
            );

            if (question == null) {
                throw new IllegalStateException(
                        "no enabled question for required category : " + category
                );
            }

            selected.add(question);
            selectedQuestionIds.add(question.getId());
        }

        for (Question candidate : candidates) {
            if (selected.size() >= rule.plannedQuestionCount()) {
                break;
            }

            if (selectedQuestionIds.add(candidate.getId())) {
                selected.add(candidate);
            }
        }

        if(selected.size() != rule.plannedQuestionCount()){
            throw new IllegalStateException(
                    "unable to build complete interview plan"
            );
        }

        return List.copyOf(selected);
    }

    private Question findFirstUnselectedByCategory(
            List<Question> candidates,
            QuestionCategory category,
            Set<Long> selectedQuestionIds
    ) {
        for (Question candidate : candidates) {
            if (candidate.getCategory() != category) {
                continue;
            }

            if (selectedQuestionIds.contains(candidate.getId())) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    private void validateCandidates(List<Question> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            Question candidate = candidates.get(index);

            if (candidate == null) {
                throw new IllegalArgumentException(
                        "candidate must not be null at index " + index
                );
            }

            if (candidate.getId() == null) {
                throw new IllegalArgumentException(
                        "candidate.id must not be null at index " + index
                );
            }

            if (candidate.getCategory() == null) {
                throw new IllegalArgumentException(
                        "candidate.category must not be null at index " + index
                );
            }
        }
    }
}
