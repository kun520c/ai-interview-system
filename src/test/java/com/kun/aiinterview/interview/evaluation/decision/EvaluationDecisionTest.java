package com.kun.aiinterview.interview.evaluation.decision;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationDecisionTest {

    @ParameterizedTest
    @MethodSource("legalDecisions")
    void shouldAcceptLegalDecisionCombinations(
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction
    ) {
        EvaluationDecision decision = new EvaluationDecision(
                evaluationPhase,
                decisionAction
        );

        assertAll(
                () -> assertEquals(evaluationPhase, decision.evaluationPhase()),
                () -> assertEquals(decisionAction, decision.decisionAction())
        );
    }

    @Test
    void shouldRejectNullEvaluationPhase() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(null, DecisionAction.NEXT_MAIN)
        );
    }

    @Test
    void shouldRejectNullDecisionAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(EvaluationPhase.FINAL, null)
        );
    }

    @ParameterizedTest
    @MethodSource("illegalDecisions")
    void shouldRejectIllegalDecisionCombinations(
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationDecision(evaluationPhase, decisionAction)
        );
    }

    private static Stream<Arguments> legalDecisions() {
        return Stream.of(
                Arguments.of(EvaluationPhase.INITIAL, DecisionAction.FOLLOW_UP),
                Arguments.of(EvaluationPhase.FINAL, DecisionAction.NEXT_MAIN),
                Arguments.of(EvaluationPhase.FINAL, DecisionAction.FINISH)
        );
    }

    private static Stream<Arguments> illegalDecisions() {
        return Stream.of(
                Arguments.of(EvaluationPhase.INITIAL, DecisionAction.NEXT_MAIN),
                Arguments.of(EvaluationPhase.INITIAL, DecisionAction.FINISH),
                Arguments.of(EvaluationPhase.FINAL, DecisionAction.FOLLOW_UP)
        );
    }
}
