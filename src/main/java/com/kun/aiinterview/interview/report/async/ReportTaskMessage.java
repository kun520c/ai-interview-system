package com.kun.aiinterview.interview.report.async;

/** Stable identifiers only: no user data, prompt, credentials or mutable execution token. */
public record ReportTaskMessage(Long taskId, Long sessionId, Long attemptVersion) {
    public void validate() {
        if (taskId == null || taskId <= 0 || sessionId == null || sessionId <= 0
                || attemptVersion == null || attemptVersion <= 0 || attemptVersion > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid report message identifiers");
        }
    }
}
