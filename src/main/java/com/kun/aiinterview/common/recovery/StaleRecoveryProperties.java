package com.kun.aiinterview.common.recovery;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "recovery")
public class StaleRecoveryProperties {

    public static final Duration DEFAULT_EVALUATING_STALE_THRESHOLD =
            Duration.ofMinutes(10);
    public static final Duration DEFAULT_GENERATING_STALE_THRESHOLD =
            Duration.ofMinutes(10);
    public static final Duration DEFAULT_PROCESSING_STALE_THRESHOLD =
            Duration.ofMinutes(15);
    public static final Duration MINIMUM_STALE_THRESHOLD =
            Duration.ofMinutes(1);

    @NotNull(message = "EVALUATING stale threshold不能为空")
    private Duration evaluatingStaleThreshold =
            DEFAULT_EVALUATING_STALE_THRESHOLD;

    @NotNull(message = "GENERATING stale threshold不能为空")
    private Duration generatingStaleThreshold =
            DEFAULT_GENERATING_STALE_THRESHOLD;

    @NotNull(message = "PROCESSING stale threshold不能为空")
    private Duration processingStaleThreshold =
            DEFAULT_PROCESSING_STALE_THRESHOLD;

    public boolean isEvaluatingStale(LocalDateTime updatedAt) {
        return isStale(updatedAt, evaluatingStaleThreshold);
    }

    public boolean isGeneratingStale(LocalDateTime updatedAt) {
        return isStale(updatedAt, generatingStaleThreshold);
    }

    public boolean isProcessingStale(LocalDateTime updatedAt) {
        return isStale(updatedAt, processingStaleThreshold);
    }

    public LocalDateTime evaluatingCutoff() {
        return cutoff(evaluatingStaleThreshold);
    }

    public LocalDateTime generatingCutoff() {
        return cutoff(generatingStaleThreshold);
    }

    public LocalDateTime processingCutoff() {
        return cutoff(processingStaleThreshold);
    }

    @AssertTrue(message = "EVALUATING stale threshold必须至少1分钟")
    public boolean isEvaluatingStaleThresholdSafe() {
        return isAtLeastMinimum(evaluatingStaleThreshold);
    }

    @AssertTrue(message = "GENERATING stale threshold必须至少1分钟")
    public boolean isGeneratingStaleThresholdSafe() {
        return isAtLeastMinimum(generatingStaleThreshold);
    }

    @AssertTrue(message = "PROCESSING stale threshold必须至少1分钟")
    public boolean isProcessingStaleThresholdSafe() {
        return isAtLeastMinimum(processingStaleThreshold);
    }

    private boolean isStale(LocalDateTime updatedAt, Duration threshold) {
        if (updatedAt == null || threshold == null) {
            return false;
        }
        return !updatedAt.isAfter(cutoff(threshold));
    }

    private LocalDateTime cutoff(Duration threshold) {
        return LocalDateTime.now().minus(threshold);
    }

    private boolean isAtLeastMinimum(Duration threshold) {
        return threshold == null
                || threshold.compareTo(MINIMUM_STALE_THRESHOLD) >= 0;
    }
}
