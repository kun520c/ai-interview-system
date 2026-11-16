package com.kun.aiinterview.interview.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InterviewReportTask {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }
    public enum Delivery { PENDING, LEASED, SENT }

    private Long id;
    private Long sessionId;
    // Read unsigned SQL versions without first narrowing to Java Integer.
    private Long attemptVersion;
    private Long executionVersion;
    private State taskState;
    private Delivery deliveryState;
    private String dispatchToken;
    private LocalDateTime dispatchLeaseUntil;
    private LocalDateTime nextSendAt;
    private int sendAttempts;
    private LocalDateTime queuedAt;
    private LocalDateTime queueDeadline;
    private LocalDateTime startedAt;
    private LocalDateTime executionDeadline;
    private String failureCode;
}
