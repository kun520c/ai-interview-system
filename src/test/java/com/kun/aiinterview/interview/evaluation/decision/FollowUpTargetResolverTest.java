package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowUpTargetResolverTest {

    private final FollowUpTargetResolver resolver =
            new FollowUpTargetResolver();

    @Test
    void shouldSelectTwoUncoveredCorePoints() {
        EvaluationContext context = context(List.of(
                point(101L, QuestionPointType.CORE),
                point(102L, QuestionPointType.CORE)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(
                        result(101L, false),
                        result(102L, false)
                )
        );

        assertEquals(List.of(101L, 102L), result);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(103L)
        );
    }

    @Test
    void shouldPutCoreBeforeOtherUncoveredPoint() {
        EvaluationContext context = context(List.of(
                point(102L, QuestionPointType.KEY),
                point(101L, QuestionPointType.CORE)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(
                        result(102L, false),
                        result(101L, false)
                )
        );

        assertEquals(List.of(101L, 102L), result);
    }

    @Test
    void shouldSelectAtMostTwoTargetPoints() {
        EvaluationContext context = context(List.of(
                point(101L, QuestionPointType.CORE),
                point(102L, QuestionPointType.CORE),
                point(103L, QuestionPointType.CORE)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(
                        result(101L, false),
                        result(102L, false),
                        result(103L, false)
                )
        );

        assertEquals(List.of(101L, 102L), result);
    }

    @Test
    void shouldReturnEmptyWhenEveryPointIsCovered() {
        EvaluationContext context = context(List.of(
                point(101L, QuestionPointType.CORE),
                point(102L, QuestionPointType.KEY)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(
                        result(101L, true),
                        result(102L, true)
                )
        );

        assertEquals(List.of(), result);
    }

    @Test
    void shouldPreserveMainSnapshotOrderInsteadOfResultOrder() {
        EvaluationContext context = context(List.of(
                point(103L, QuestionPointType.CORE),
                point(101L, QuestionPointType.CORE),
                point(102L, QuestionPointType.CORE)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(
                        result(102L, false),
                        result(101L, false),
                        result(103L, false)
                )
        );

        assertEquals(List.of(103L, 101L), result);
    }

    @Test
    void shouldNeverReturnPointOutsideMainSnapshot() {
        EvaluationContext context = context(List.of(
                point(101L, QuestionPointType.CORE)
        ));

        List<Long> result = resolver.resolve(
                context,
                List.of(result(999L, false))
        );

        assertEquals(List.of(), result);
    }

    @Test
    void shouldRejectNullCoveredValueWithoutUnboxingFailure() {
        EvaluationContext context = context(List.of(
                point(101L, QuestionPointType.CORE)
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        context,
                        List.of(new LlmEvaluationSuggestion.ScoringPointResult(
                                101L,
                                null,
                                null
                        ))
                )
        );
    }

    private EvaluationContext context(
            List<ScoringPointSnapshot> scoringPoints
    ) {
        return new EvaluationContext(
                1L,
                2L,
                1L,
                2L,
                EvaluationMode.MAIN_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                scoringPoints,
                "MAIN 回答",
                null,
                null,
                List.of()
        );
    }

    private ScoringPointSnapshot point(
            long id,
            QuestionPointType pointType
    ) {
        return new ScoringPointSnapshot(
                id,
                pointType,
                pointType + " 评分点",
                10
        );
    }

    private LlmEvaluationSuggestion.ScoringPointResult result(
            long id,
            boolean covered
    ) {
        return new LlmEvaluationSuggestion.ScoringPointResult(
                id,
                covered,
                covered ? "覆盖证据" : null
        );
    }
}
