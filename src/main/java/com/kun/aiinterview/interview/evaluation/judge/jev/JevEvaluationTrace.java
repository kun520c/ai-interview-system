package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.kun.aiinterview.common.exception.ExternalServiceException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Request-local, bounded diagnostics containing only enums, counts and durations. */
final class JevEvaluationTrace {
    enum Stage { JEV_NATIVE, DEEPSEEK_FEEDBACK, DEEPSEEK_FALLBACK }

    private record Attempt(Stage stage, String failure, long elapsedMillis) { }

    private final List<Attempt> attempts = new ArrayList<>();
    Stage stage = Stage.JEV_NATIVE;
    String actualProvider = "none";
    String fallbackStage = "NONE";
    String failure = "NONE";
    String fallbackFailure = "NONE";
    String retryStopReason = "NOT_NEEDED";
    String outcome = "FAILED";

    <T> T invoke(Stage current, JevRequestBudget budget, Duration timeout, Supplier<T> operation) {
        stage = current;
        if (budget.available(Duration.ZERO).isZero() || timeout.isZero() || timeout.isNegative()) {
            if (current == Stage.DEEPSEEK_FALLBACK) fallbackFailure = "REQUEST_BUDGET_EXHAUSTED";
            else failure = "REQUEST_BUDGET_EXHAUSTED";
            throw new JevStageException(JevStageException.Failure.REQUEST_BUDGET_EXHAUSTED);
        }

        long start = budget.elapsedMillis();
        String code = "NONE";
        try {
            return budget.call(timeout, operation);
        } catch (ExternalServiceException exception) {
            var typed = exception instanceof JevStageException known ? known : new JevStageException(switch (current) {
                case JEV_NATIVE -> JevStageException.Failure.NATIVE_CALL_FAILED;
                case DEEPSEEK_FEEDBACK -> JevStageException.Failure.FEEDBACK_CALL_FAILED;
                case DEEPSEEK_FALLBACK -> JevStageException.Failure.FALLBACK_CALL_FAILED;
            });
            code = typed.failure.name();
            if (current == Stage.DEEPSEEK_FALLBACK) fallbackFailure = code;
            else failure = code;
            throw current == Stage.DEEPSEEK_FALLBACK ? exception : typed;
        } catch (RuntimeException | Error exception) {
            code = "INTERNAL_ERROR";
            if (current == Stage.DEEPSEEK_FALLBACK) fallbackFailure = code;
            else failure = code;
            throw exception;
        } finally {
            attempts.add(new Attempt(current, code, budget.elapsedMillis() - start));
        }
    }

    record Snapshot(String actualProvider, String fallbackStage, String failureClassification,
                    String fallbackFailure, String retryStopReason, String outcome,
                    long nativeCalls, long feedbackCalls, long fallbackCalls,
                    long elapsedMillis, List<Attempt> attempts) { }

    Snapshot snapshot(JevRequestBudget budget) {
        return new Snapshot(actualProvider, fallbackStage, failure, fallbackFailure, retryStopReason, outcome,
                count(Stage.JEV_NATIVE), count(Stage.DEEPSEEK_FEEDBACK), count(Stage.DEEPSEEK_FALLBACK),
                budget.elapsedMillis(), List.copyOf(attempts));
    }

    private long count(Stage expected) {
        return attempts.stream().filter(attempt -> attempt.stage() == expected).count();
    }
}
