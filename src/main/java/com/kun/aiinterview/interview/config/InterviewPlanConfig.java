package com.kun.aiinterview.interview.config;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InterviewPlanConfig {

    private static final Map<QuestionDifficulty, InterviewPlanRule> RULES = Map.of(
            QuestionDifficulty.EASY,
            new InterviewPlanRule(
                    5,
                    List.of(
                            QuestionCategory.JAVA_BASIC,
                            QuestionCategory.JAVA_COLLECTION,
                            QuestionCategory.SPRING,
                            QuestionCategory.MYSQL,
                            QuestionCategory.NETWORK
                    )
            ),

            QuestionDifficulty.MEDIUM,
            new InterviewPlanRule(
                    7,
                    List.of(
                            QuestionCategory.JAVA_BASIC,
                            QuestionCategory.JAVA_COLLECTION,
                            QuestionCategory.JVM,
                            QuestionCategory.JAVA_CONCURRENCY,
                            QuestionCategory.SPRING,
                            QuestionCategory.MYSQL,
                            QuestionCategory.REDIS
                    )
            ),

            QuestionDifficulty.HARD,
            new InterviewPlanRule(
                    9,
                    List.of(
                            QuestionCategory.JAVA_BASIC,
                            QuestionCategory.JAVA_COLLECTION,
                            QuestionCategory.JVM,
                            QuestionCategory.JAVA_CONCURRENCY,
                            QuestionCategory.SPRING,
                            QuestionCategory.MYSQL,
                            QuestionCategory.REDIS,
                            QuestionCategory.NETWORK,
                            QuestionCategory.PROJECT_SCENARIO
                    )
            )
    );

    public InterviewPlanRule getRule(QuestionDifficulty difficulty) {
        if (difficulty == null) {
            throw new IllegalArgumentException("difficulty must not be null");
        }

        InterviewPlanRule rule = RULES.get(difficulty);

        if (rule == null) {
            throw new IllegalArgumentException(
                    "unsupported interview difficulty: " + difficulty
            );
        }

        return rule;
    }

    public record InterviewPlanRule(
            int plannedQuestionCount,
            List<QuestionCategory> requiredCategories
    ) {

        public InterviewPlanRule {
            if (plannedQuestionCount <= 0) {
                throw new IllegalArgumentException(
                        "plannedQuestionCount must be positive"
                );
            }

            if (requiredCategories == null || requiredCategories.isEmpty()) {
                throw new IllegalArgumentException(
                        "requiredCategories must not be empty"
                );
            }

            requiredCategories = List.copyOf(requiredCategories);

            if (Set.copyOf(requiredCategories).size()
                    != requiredCategories.size()) {
                throw new IllegalArgumentException(
                        "requiredCategories must not contain duplicates"
                );
            }

            if (requiredCategories.size() > plannedQuestionCount) {
                throw new IllegalArgumentException(
                        "requiredCategories cannot exceed plannedQuestionCount"
                );
            }
        }
    }
}
