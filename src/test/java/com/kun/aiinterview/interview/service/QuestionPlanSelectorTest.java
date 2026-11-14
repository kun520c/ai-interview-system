package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.config.InterviewPlanConfig.InterviewPlanRule;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.kun.aiinterview.question.enums.QuestionCategory.JAVA_BASIC;
import static com.kun.aiinterview.question.enums.QuestionCategory.MYSQL;
import static com.kun.aiinterview.question.enums.QuestionCategory.REDIS;
import static com.kun.aiinterview.question.enums.QuestionCategory.SPRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionPlanSelectorTest {

    private final QuestionPlanSelector selector = new QuestionPlanSelector();

    @ParameterizedTest
    @CsvSource({"EASY, 5", "MEDIUM, 7", "HARD, 9"})
    void shouldSelectExactlyOneQuestionPerRequiredCategoryInPlanOrder(
            QuestionDifficulty difficulty, int expectedCount
    ) {
        InterviewPlanRule rule = new InterviewPlanConfig().getRule(difficulty);
        List<Question> candidates = new ArrayList<>(candidatesFor(rule));
        List<Question> originalOrder = List.copyOf(candidates);

        List<Question> selected = selector.select(candidates, rule, bound -> bound - 1);

        assertThat(selected).hasSize(expectedCount);
        assertThat(selected).extracting(Question::getCategory)
                .containsExactlyElementsOf(rule.requiredCategories());
        assertThat(selected).extracting(Question::getId).doesNotHaveDuplicates();
        assertThat(candidates).containsExactlyElementsOf(originalOrder);
        assertThatThrownBy(selected::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldExcludeDisabledAndUnrelatedCandidatesAndDeduplicateIdenticalRows() {
        InterviewPlanRule rule = rule(MYSQL, JAVA_BASIC);
        Question disabled = question(1, MYSQL);
        disabled.setStatus(QuestionStatus.DISABLED);
        Question firstMysql = question(2, MYSQL);
        Question secondMysql = question(3, MYSQL);
        Question java = question(4, JAVA_BASIC);

        List<Question> selected = selector.select(
                List.of(question(5, REDIS), java, secondMysql, disabled, firstMysql, question(2, MYSQL)),
                rule,
                bound -> bound - 1
        );

        assertThat(selected).containsExactly(secondMysql, java);
        assertThat(selector.select(
                List.of(disabled, secondMysql, firstMysql, java, question(5, REDIS)),
                rule,
                bound -> 0
        )).containsExactly(firstMysql, java);
    }

    @Test
    void shouldGiveEachUniqueCandidateOneReachableIndex() {
        InterviewPlanRule rule = rule(JAVA_BASIC);
        List<Question> candidates = List.of(
                question(30, JAVA_BASIC),
                question(10, JAVA_BASIC),
                question(20, JAVA_BASIC),
                question(10, JAVA_BASIC)
        );
        List<Long> selectedIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            int fixedIndex = index;
            List<Question> selected = selector.select(candidates, rule, bound -> {
                assertThat(bound).isEqualTo(3);
                return fixedIndex;
            });
            selectedIds.add(selected.getFirst().getId());
        }
        assertThat(selectedIds).containsExactly(10L, 20L, 30L);
    }

    @Test
    void shouldReplayTheSamePlanWithFixedSeedEvenIfCandidateOrderChanges() {
        InterviewPlanRule rule = rule(JAVA_BASIC, SPRING, MYSQL);
        List<Question> candidates = candidatesFor(rule);
        var random = new SplittableRandom(20261114L);
        var replay = new SplittableRandom(20261114L);

        List<Question> selected = selector.select(candidates, rule, random::nextInt);

        assertThat(selector.select(candidates.reversed(), rule, replay::nextInt))
                .containsExactlyElementsOf(selected);
    }

    @Test
    void fixedSeedSampleShouldShowNoObviousFirstCandidateBias() {
        InterviewPlanRule rule = rule(JAVA_BASIC);
        List<Question> candidates = candidatesFor(rule);
        var random = new SplittableRandom(20261114L);
        int[] frequencies = new int[3];
        for (int draw = 0; draw < 300; draw++) {
            long selectedId = selector.select(candidates, rule, random::nextInt).getFirst().getId();
            frequencies[(int) selectedId - 1]++;
        }

        for (int frequency : frequencies) {
            assertThat(frequency).isBetween(50, 150);
        }
    }

    @Test
    void shouldKeepConcurrentDrawsAndTheirResultsIndependent() throws Exception {
        InterviewPlanRule rule = rule(JAVA_BASIC, SPRING, MYSQL);
        List<Question> candidates = candidatesFor(rule);
        var concurrentDraw = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selector.select(
                    candidates, rule, bound -> awaitDraw(concurrentDraw, 0)
            ));
            var last = executor.submit(() -> selector.select(
                    candidates, rule, bound -> awaitDraw(concurrentDraw, bound - 1)
            ));

            assertThat(first.get(10, TimeUnit.SECONDS)).extracting(Question::getId)
                    .containsExactly(1L, 4L, 7L);
            assertThat(last.get(10, TimeUnit.SECONDS)).extracting(Question::getId)
                    .containsExactly(3L, 6L, 9L);
        }
    }

    @Test
    void shouldBuildValidPlansWhenProductionRandomSourceIsUsedConcurrently() throws Exception {
        InterviewPlanRule rule = rule(JAVA_BASIC, SPRING, MYSQL);
        List<Question> candidates = candidatesFor(rule);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selector.select(candidates, rule));
            var second = executor.submit(() -> selector.select(candidates, rule));

            for (var result : List.of(first, second)) {
                List<Question> plan = result.get(10, TimeUnit.SECONDS);
                assertThat(plan).extracting(Question::getCategory)
                        .containsExactlyElementsOf(rule.requiredCategories());
                assertThat(plan).extracting(Question::getId).doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void shouldRejectInsufficientCandidates() {
        assertThatThrownBy(() -> selector.select(
                List.of(question(1, JAVA_BASIC)),
                rule(JAVA_BASIC, MYSQL)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required category")
                .hasMessageContaining("MYSQL");
    }

    @Test
    void shouldRejectMissingRequiredCategory() {
        Question disabled = question(3, MYSQL);
        disabled.setStatus(QuestionStatus.DISABLED);
        assertThatThrownBy(() -> selector.select(
                List.of(
                        question(1, JAVA_BASIC),
                        question(2, SPRING),
                        disabled
                ),
                rule(JAVA_BASIC, MYSQL)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required category")
                .hasMessageContaining("MYSQL");
    }

    @Test
    void shouldRejectEmptyCandidateListWithMissingCategory() {
        assertThatThrownBy(() -> selector.select(List.of(), rule(JAVA_BASIC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JAVA_BASIC");
    }

    @Test
    void shouldRejectPlanThatWouldRequireFillingFromOtherCategories() {
        assertThatThrownBy(() -> selector.select(
                List.of(question(1, JAVA_BASIC), question(2, MYSQL)),
                new InterviewPlanRule(2, List.of(JAVA_BASIC))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one question per category");
    }

    @Test
    void shouldRejectConflictingCandidateIdsAcrossCategories() {
        assertThatThrownBy(() -> selector.select(
                List.of(question(1, JAVA_BASIC), question(1, MYSQL)),
                rule(JAVA_BASIC, MYSQL)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting candidates with id 1");
    }

    @Test
    void shouldRejectNullInputs() {
        List<Question> candidates = List.of(question(1, JAVA_BASIC));
        InterviewPlanRule rule = rule(JAVA_BASIC);

        assertThatThrownBy(() -> selector.select(null, rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidates must not be null");
        assertThatThrownBy(() -> selector.select(candidates, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rule must not be null");
        assertThatThrownBy(() -> selector.select(candidates, rule, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("randomIndex must not be null");
    }

    @Test
    void shouldRejectNullCandidate() {
        List<Question> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(question(1, JAVA_BASIC));

        assertThatThrownBy(() -> selector.select(
                candidates,
                rule(JAVA_BASIC)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate must not be null");
    }

    @Test
    void shouldRejectCandidateWithoutIdOrCategory() {
        assertThatThrownBy(() -> selector.select(
                List.of(Question.builder()
                        .category(JAVA_BASIC)
                        .build()),
                rule(JAVA_BASIC)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate.id");

        assertThatThrownBy(() -> selector.select(
                List.of(question(0, JAVA_BASIC)),
                rule(JAVA_BASIC)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate.id");

        assertThatThrownBy(() -> selector.select(
                List.of(Question.builder().id(1L).build()),
                rule(JAVA_BASIC)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate.category");
    }

    private static int awaitDraw(CyclicBarrier barrier, int index) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return index;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent selection interrupted", e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new AssertionError("concurrent selections did not reach the same draw", e);
        }
    }

    private InterviewPlanRule rule(QuestionCategory... categories) {
        return new InterviewPlanRule(categories.length, List.of(categories));
    }

    private List<Question> candidatesFor(InterviewPlanRule rule) {
        List<Question> candidates = new ArrayList<>();
        for (QuestionCategory category : rule.requiredCategories()) {
            for (int i = 0; i < 3; i++) {
                candidates.add(question(candidates.size() + 1, category));
            }
        }
        return List.copyOf(candidates);
    }

    private Question question(long id, QuestionCategory category) {
        return Question.builder().id(id).category(category).status(QuestionStatus.ENABLED).build();
    }
}
