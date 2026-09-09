package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationDecisionTest {

    @ParameterizedTest
    @MethodSource("legalDecisions")
    void shouldAcceptLegalDecisionCombinations(
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction,
            List<Long> targetPointIds
    ) {
        EvaluationDecision decision = new EvaluationDecision(
                evaluationPhase,
                decisionAction,
                targetPointIds
        );

        assertAll(
                () -> assertEquals(
                        evaluationPhase,
                        decision.evaluationPhase()
                ),
                () -> assertEquals(
                        decisionAction,
                        decision.decisionAction()
                ),
                () -> assertEquals(
                        targetPointIds,
                        decision.followUpTargetPointIds()
                )
        );
    }

    @Test
    void shouldRejectNullEvaluationPhase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        null,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullDecisionAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        null,
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullTargetPointIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        null
                )
        );
    }

    @ParameterizedTest
    @MethodSource("illegalPhaseAndActionCombinations")
    void shouldRejectIllegalPhaseAndActionCombinations(
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction,
            List<Long> targetPointIds
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        evaluationPhase,
                        decisionAction,
                        targetPointIds
                )
        );
    }

    @Test
    void shouldRejectFollowUpWithoutTargetPoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FOLLOW_UP,
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectFollowUpWithMoreThanTwoTargetPoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FOLLOW_UP,
                        List.of(101L, 102L, 103L)
                )
        );
    }

    @ParameterizedTest
    @MethodSource("finalActions")
    void shouldRejectFinalActionWithTargetPoint(
            DecisionAction action
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(
                        EvaluationPhase.FINAL,
                        action,
                        List.of(101L)
                )
        );
    }

    @Test
    void shouldDefensivelyCopyTargetPointIds() {
        List<Long> mutableTargets =
                new ArrayList<>(List.of(101L));

        EvaluationDecision decision = new EvaluationDecision(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                mutableTargets
        );

        mutableTargets.add(102L);

        assertAll(
                () -> assertEquals(
                        List.of(101L),
                        decision.followUpTargetPointIds()
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> decision
                                .followUpTargetPointIds()
                                .add(102L)
                )
        );
    }

    private static Stream<Arguments> legalDecisions() {
        return Stream.of(
                Arguments.of(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FOLLOW_UP,
                        List.of(101L)
                ),
                Arguments.of(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FOLLOW_UP,
                        List.of(101L, 102L)
                ),
                Arguments.of(
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                Arguments.of(
                        EvaluationPhase.FINAL,
                        DecisionAction.FINISH,
                        List.of()
                )
        );
    }

    private static Stream<Arguments>
            illegalPhaseAndActionCombinations() {
        return Stream.of(
                Arguments.of(
                        EvaluationPhase.INITIAL,
                        DecisionAction.NEXT_MAIN,
                        List.of()
                ),
                Arguments.of(
                        EvaluationPhase.INITIAL,
                        DecisionAction.FINISH,
                        List.of()
                ),
                Arguments.of(
                        EvaluationPhase.FINAL,
                        DecisionAction.FOLLOW_UP,
                        List.of(101L)
                )
        );
    }

    private static Stream<DecisionAction> finalActions() {
        return Stream.of(
                DecisionAction.NEXT_MAIN,
                DecisionAction.FINISH
        );
    }
}
