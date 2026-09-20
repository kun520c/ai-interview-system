package com.kun.aiinterview.common.recovery;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StaleRecoveryPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldUseSafeDefaultThresholds() {
        StaleRecoveryProperties properties = new StaleRecoveryProperties();

        assertThat(properties.getEvaluatingStaleThreshold())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getGeneratingStaleThreshold())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getProcessingStaleThreshold())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void shouldTreatNullUpdatedAtAsNotStale() {
        StaleRecoveryProperties properties = new StaleRecoveryProperties();

        assertThat(properties.isEvaluatingStale(null)).isFalse();
        assertThat(properties.isGeneratingStale(null)).isFalse();
        assertThat(properties.isProcessingStale(null)).isFalse();
    }

    @Test
    void shouldTreatRecentUpdatesAsFreshAndOldUpdatesAsStale() {
        StaleRecoveryProperties properties = new StaleRecoveryProperties();
        LocalDateTime now = LocalDateTime.now();

        assertThat(properties.isEvaluatingStale(now)).isFalse();
        assertThat(properties.isEvaluatingStale(now.minusMinutes(9))).isFalse();
        assertThat(properties.isEvaluatingStale(now.minusMinutes(11))).isTrue();
        assertThat(properties.isGeneratingStale(now.minusMinutes(11))).isTrue();
        assertThat(properties.isProcessingStale(now.minusMinutes(14))).isFalse();
        assertThat(properties.isProcessingStale(now.minusMinutes(16))).isTrue();
    }

    @Test
    void shouldRejectThresholdsShorterThanOneMinute() {
        StaleRecoveryProperties properties = new StaleRecoveryProperties();
        properties.setEvaluatingStaleThreshold(Duration.ofSeconds(30));
        properties.setGeneratingStaleThreshold(Duration.ofSeconds(59));
        properties.setProcessingStaleThreshold(Duration.ofMillis(1));

        Set<ConstraintViolation<StaleRecoveryProperties>> violations =
                validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "evaluatingStaleThresholdSafe",
                        "generatingStaleThresholdSafe",
                        "processingStaleThresholdSafe"
                );
    }
}
