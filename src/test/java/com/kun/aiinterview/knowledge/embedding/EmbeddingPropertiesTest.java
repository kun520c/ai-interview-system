package com.kun.aiinterview.knowledge.embedding;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPropertiesTest {

    private static final String TEST_API_KEY = "unit-test-api-key";

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
    void shouldAcceptCompleteValidProperties() {
        assertThat(validator.validate(validProperties())).isEmpty();
    }

    @Test
    void shouldRegisterValidatedConfigurationProperties() {
        EnableConfigurationProperties registration =
                EmbeddingConfiguration.class.getAnnotation(
                        EnableConfigurationProperties.class
                );

        assertThat(EmbeddingProperties.class).hasAnnotation(Validated.class);
        assertThat(registration).isNotNull();
        assertThat(registration.value())
                .containsExactly(EmbeddingProperties.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankApiKey(String apiKey) {
        EmbeddingProperties properties = validProperties();
        properties.setApiKey(apiKey);

        assertViolationOn(properties, "apiKey");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveDimension(int dimension) {
        EmbeddingProperties properties = validProperties();
        properties.setDimension(dimension);

        assertViolationOn(properties, "dimension");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveBatchSize(int batchSize) {
        EmbeddingProperties properties = validProperties();
        properties.setBatchSize(batchSize);

        assertViolationOn(properties, "batchSize");
    }

    @Test
    void shouldRejectMissingConnectTimeout() {
        EmbeddingProperties properties = validProperties();
        properties.setConnectTimeout(null);

        assertViolationOn(properties, "connectTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void shouldRejectNonPositiveConnectTimeout(long seconds) {
        EmbeddingProperties properties = validProperties();
        properties.setConnectTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "connectTimeoutPositive");
    }

    @Test
    void shouldRejectMissingReadTimeout() {
        EmbeddingProperties properties = validProperties();
        properties.setReadTimeout(null);

        assertViolationOn(properties, "readTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void shouldRejectNonPositiveReadTimeout(long seconds) {
        EmbeddingProperties properties = validProperties();
        properties.setReadTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "readTimeoutPositive");
    }

    @Test
    void shouldNotExposeApiKeyThroughToString() {
        EmbeddingProperties properties = validProperties();

        assertThat(properties.toString())
                .doesNotContain(TEST_API_KEY)
                .doesNotContain("apiKey");
    }

    private static EmbeddingProperties validProperties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setBaseUrl(URI.create("http://localhost:18080/v1"));
        properties.setApiKey(TEST_API_KEY);
        properties.setModel("test-model");
        properties.setDimension(3);
        properties.setBatchSize(2);
        properties.setProfileVersion("test-profile-v1");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static void assertViolationOn(
            EmbeddingProperties properties,
            String propertyPath
    ) {
        Set<ConstraintViolation<EmbeddingProperties>> violations =
                validator.validate(properties);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .contains(propertyPath);
    }
}
