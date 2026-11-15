package com.kun.aiinterview.interview.report.async;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("report.async")
public class ReportAsyncProperties {
    private boolean enabled;
    private boolean accepting = true;
    private boolean wakeupEnabled = true;
    @NotBlank private String nameserver = "127.0.0.1:19876";
    @NotBlank private String topic = "ai-interview-report";
    @NotBlank private String consumerGroup = "ai-interview-report-consumer";
    @Min(1) @Max(100) private int scanBatchSize = 16;
    @Min(100) @Max(10000) private int sendTimeoutMs = 5000;
    @Min(1) @Max(300) private int dispatchLeaseSeconds = 30;
    @Min(1) @Max(100) private int maxSendAttempts = 20;
    @Min(1) @Max(86400) private int queueDeadlineSeconds = 600;
    @Min(1) @Max(86400) private int executionDeadlineSeconds = 600;
    @Min(1) @Max(8) private int consumerThreads = 4;
    @Min(1) @Max(16) private int maxReconsumeTimes = 5;
    @Min(1) @Max(60) private int consumerTimeoutMinutes = 12;
    @Min(100) @Max(60000) private int scanIntervalMs = 500;

    @AssertTrue(message = "dispatch lease must exceed send timeout plus 5s settlement margin")
    public boolean isLeaseBudgetValid() {
        return dispatchLeaseSeconds * 1000L > sendTimeoutMs + 5000L;
    }

    @AssertTrue(message = "MQ consume timeout must exceed execution deadline")
    public boolean isConsumeBudgetValid() {
        return consumerTimeoutMinutes * 60L > executionDeadlineSeconds;
    }

    public int backoffSeconds(int attempts) {
        return (int) Math.min(60L, 1L << Math.min(6, Math.max(1, attempts)));
    }
}
