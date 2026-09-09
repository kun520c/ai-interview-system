package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScore;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.validation.ValidatedEvaluationSuggestion;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FollowUpPolicyTest {

    private final FollowUpPolicy followUpPolicy =
            new FollowUpPolicy(new FollowUpTargetResolver());

    @Test
    void shouldFollowUpForMainAnswerAtScore59WhenLlmRecommends() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.KEY, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, List.of(scoringResult(101L, false))),
                score(59),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP
        );
        assertEquals(
                List.of(101L),
                decision.followUpTargetPointIds()
        );
    }

    @Test
    void shouldNotFollowUpAtScore60AndShouldSelectNextMain() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, List.of(scoringResult(101L, true))),
                score(60),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldNotFollowUpAtScore60AndShouldFinishLastQuestion() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, List.of(scoringResult(101L, true))),
                score(60),
                false
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.FINISH
        );
    }

    @Test
    void shouldFollowUpForHighScoreMainAnswerWithUncoveredCorePoint() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, List.of(scoringResult(101L, false))),
                score(80),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP
        );
        assertEquals(
                List.of(101L),
                decision.followUpTargetPointIds()
        );
    }

    @Test
    void shouldUseFinalDecisionWhenNoLegalTargetPointExists() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, List.of(scoringResult(101L, true))),
                score(40),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
        assertEquals(
                List.of(),
                decision.followUpTargetPointIds()
        );
    }

    @Test
    void shouldIgnoreUncoveredNonCorePointsInMultipleScoringPoints() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 25),
                scoringPoint(102L, QuestionPointType.KEY, 25),
                scoringPoint(103L, QuestionPointType.PRINCIPLE, 25),
                scoringPoint(104L, QuestionPointType.INTERNAL, 25)
        );
        List<LlmEvaluationSuggestion.ScoringPointResult> results = List.of(
                scoringResult(101L, true),
                scoringResult(102L, false),
                scoringResult(103L, false),
                scoringResult(104L, false)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(true, results),
                score(80),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldNotFollowUpForLowScoreWhenLlmDoesNotRecommend() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, true))),
                score(40),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldNotFollowUpForUncoveredCoreWhenLlmDoesNotRecommend() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, false))),
                score(80),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldSelectNextMainForNormalMainAnswerWhenAnotherMainExists() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, true))),
                score(80),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldFinishForNormalMainAnswerWhenNoMainRemains() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.MAIN_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, true))),
                score(80),
                false
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.FINISH
        );
    }

    @Test
    void shouldNeverFollowUpFollowUpAnswerWhenAnotherMainExists() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.FOLLOW_UP_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, false))),
                score(40),
                true
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
    }

    @Test
    void shouldNeverFollowUpFollowUpAnswerWhenNoMainRemains() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        EvaluationDecision decision = followUpPolicy.decide(
                context(EvaluationMode.FOLLOW_UP_ANSWER, points),
                suggestion(false, List.of(scoringResult(101L, false))),
                score(40),
                false
        );

        assertDecision(
                decision,
                EvaluationPhase.FINAL,
                DecisionAction.FINISH
        );
    }

    @Test
    void shouldRejectNullContext() {
        assertThrows(
                IllegalArgumentException.class,
                () -> followUpPolicy.decide(
                        null,
                        suggestion(
                                false,
                                List.of(scoringResult(101L, true))
                        ),
                        score(80),
                        true
                )
        );
    }

    @Test
    void shouldRejectNullSuggestion() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> followUpPolicy.decide(
                        context(EvaluationMode.MAIN_ANSWER, points),
                        null,
                        score(80),
                        true
                )
        );
    }

    @Test
    void shouldRejectNullScore() {
        List<ScoringPointSnapshot> points = List.of(
                scoringPoint(101L, QuestionPointType.CORE, 100)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> followUpPolicy.decide(
                        context(EvaluationMode.MAIN_ANSWER, points),
                        suggestion(
                                false,
                                List.of(scoringResult(101L, true))
                        ),
                        null,
                        true
                )
        );
    }

    private EvaluationContext context(
            EvaluationMode mode,
            List<ScoringPointSnapshot> scoringPoints
    ) {
        boolean followUpAnswer = mode == EvaluationMode.FOLLOW_UP_ANSWER;

        return new EvaluationContext(
                followUpAnswer ? 3L : 1L,
                followUpAnswer ? 4L : 2L,
                1L,
                2L,
                mode,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                scoringPoints,
                "MAIN 回答",
                followUpAnswer ? "请进一步说明扩容过程" : null,
                followUpAnswer ? "FOLLOW_UP 回答" : null,
                followUpAnswer
                        ? List.of(scoringPoints.getFirst().scoringPointId())
                        : List.of()
        );
    }

    private ValidatedEvaluationSuggestion suggestion(
            boolean followUpRecommended,
            List<LlmEvaluationSuggestion.ScoringPointResult> results
    ) {
        return new ValidatedEvaluationSuggestion(
                16,
                16,
                16,
                16,
                16,
                List.of("核心概念准确"),
                List.of("可以补充实现细节"),
                null,
                results,
                followUpRecommended,
                followUpRecommended ? "请进一步说明实现细节" : null
        );
    }

    private EvaluationScore score(int totalScore) {
        int baseScore = totalScore / 5;
        int remainder = totalScore % 5;

        return new EvaluationScore(
                baseScore + (remainder > 0 ? 1 : 0),
                baseScore + (remainder > 1 ? 1 : 0),
                baseScore + (remainder > 2 ? 1 : 0),
                baseScore + (remainder > 3 ? 1 : 0),
                baseScore,
                totalScore
        );
    }

    private ScoringPointSnapshot scoringPoint(
            long scoringPointId,
            QuestionPointType pointType,
            int weight
    ) {
        return new ScoringPointSnapshot(
                scoringPointId,
                pointType,
                pointType + " 评分点",
                weight
        );
    }

    private LlmEvaluationSuggestion.ScoringPointResult scoringResult(
            long scoringPointId,
            boolean covered
    ) {
        return new LlmEvaluationSuggestion.ScoringPointResult(
                scoringPointId,
                covered,
                covered ? "用户回答中的覆盖证据" : null
        );
    }

    private void assertDecision(
            EvaluationDecision decision,
            EvaluationPhase expectedPhase,
            DecisionAction expectedAction
    ) {
        assertAll(
                () -> assertEquals(expectedPhase, decision.evaluationPhase()),
                () -> assertEquals(expectedAction, decision.decisionAction()),
                () -> {
                    if (expectedAction != DecisionAction.FOLLOW_UP) {
                        assertEquals(
                                List.of(),
                                decision.followUpTargetPointIds()
                        );
                    }
                }
        );
    }
}
