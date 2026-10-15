package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.kun.aiinterview.common.exception.ExternalServiceException;

/** Fixed codes only: never retain provider bodies or parser messages in diagnostics. */
final class JevStageException extends ExternalServiceException {
    enum Failure {
        NATIVE_CALL_FAILED(false), NATIVE_DECISION_INVALID(false),
        FEEDBACK_CALL_FAILED(false), FEEDBACK_JSON_INVALID(true),
        FEEDBACK_EVIDENCE_MISSING(true), FEEDBACK_EVIDENCE_MISMATCH(true),
        FEEDBACK_SCHEMA_INVALID(true), FEEDBACK_VALIDATION_FAILED(false), FALLBACK_CALL_FAILED(false),
        CALL_TIMEOUT(false), REQUEST_BUDGET_EXHAUSTED(false), INTERRUPTED(false);

        final boolean retryable;

        Failure(boolean retryable) {
            this.retryable = retryable;
        }
    }

    final Failure failure;

    JevStageException(Failure failure) {
        super("Judge stage failed: " + failure.name());
        this.failure = failure;
    }
}
