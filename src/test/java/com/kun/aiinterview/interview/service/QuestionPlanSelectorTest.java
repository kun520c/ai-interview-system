package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig.InterviewPlanRule;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionPlanSelectorTest {

    private final QuestionPlanSelector selector = new QuestionPlanSelector();

    @Test
    void shouldCoverRequiredCategoriesWithoutDuplicateQuestions() {
        List<Question> selected = selector.select(
                List.of(
                        question(1, QuestionCategory.SPRING),
                        question(2, QuestionCategory.JAVA_BASIC),
                        question(2, QuestionCategory.JAVA_BASIC),
                        question(3, QuestionCategory.MYSQL),
                        question(4, QuestionCategory.REDIS)
                ),
                new InterviewPlanRule(
                        4,
                        List.of(
                                QuestionCategory.JAVA_BASIC,
                                QuestionCategory.MYSQL
                        )
                )
        );

        assertThat(selected).hasSize(4);
        assertThat(selected).extracting(Question::getCategory)
                .contains(QuestionCategory.JAVA_BASIC, QuestionCategory.MYSQL);
        assertThat(selected).extracting(Question::getId)
                .doesNotHaveDuplicates();
        assertThatThrownBy(() -> selected.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInsufficientCandidates() {
        assertThatThrownBy(() -> selector.select(
                List.of(question(1, QuestionCategory.JAVA_BASIC)),
                new InterviewPlanRule(2, List.of(QuestionCategory.JAVA_BASIC))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enough");
    }

    @Test
    void shouldRejectMissingRequiredCategory() {
        assertThatThrownBy(() -> selector.select(
                List.of(
                        question(1, QuestionCategory.JAVA_BASIC),
                        question(2, QuestionCategory.SPRING)
                ),
                new InterviewPlanRule(2, List.of(QuestionCategory.MYSQL))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required category");
    }

    @Test
    void shouldRejectNullCandidate() {
        List<Question> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(question(1, QuestionCategory.JAVA_BASIC));

        assertThatThrownBy(() -> selector.select(
                candidates,
                new InterviewPlanRule(1, List.of(QuestionCategory.JAVA_BASIC))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate must not be null");
    }

    @Test
    void shouldRejectCandidateWithoutIdOrCategory() {
        assertThatThrownBy(() -> selector.select(
                List.of(Question.builder()
                        .category(QuestionCategory.JAVA_BASIC)
                        .build()),
                new InterviewPlanRule(1, List.of(QuestionCategory.JAVA_BASIC))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate.id");

        assertThatThrownBy(() -> selector.select(
                List.of(Question.builder().id(1L).build()),
                new InterviewPlanRule(1, List.of(QuestionCategory.JAVA_BASIC))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate.category");
    }

    private Question question(long id, QuestionCategory category) {
        return Question.builder().id(id).category(category).build();
    }
}
