package com.kun.aiinterview.interview.config;

import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewPlanConfigTest {

    private final InterviewPlanConfig config = new InterviewPlanConfig();

    @Test
    void shouldProvideRuleForEveryDifficultyWithExpectedCount() {
        assertThat(config.getRule(QuestionDifficulty.EASY).plannedQuestionCount())
                .isEqualTo(5);
        assertThat(config.getRule(QuestionDifficulty.MEDIUM).plannedQuestionCount())
                .isEqualTo(7);
        assertThat(config.getRule(QuestionDifficulty.HARD).plannedQuestionCount())
                .isEqualTo(9);

        for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
            var rule = config.getRule(difficulty);
            assertThat(rule.requiredCategories()).isNotEmpty();
            assertThat(rule.requiredCategories().size())
                    .isLessThanOrEqualTo(rule.plannedQuestionCount());
            assertThat(new HashSet<>(rule.requiredCategories()))
                    .hasSameSizeAs(rule.requiredCategories());
        }
    }

    @Test
    void requiredCategoriesShouldBeImmutable() {
        var categories = config.getRule(QuestionDifficulty.EASY)
                .requiredCategories();

        assertThatThrownBy(() -> categories.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ruleShouldRejectDuplicateRequiredCategories() {
        assertThatThrownBy(() -> new InterviewPlanConfig.InterviewPlanRule(
                2,
                List.of(
                        config.getRule(QuestionDifficulty.EASY)
                                .requiredCategories().get(0),
                        config.getRule(QuestionDifficulty.EASY)
                                .requiredCategories().get(0)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
    }
}
