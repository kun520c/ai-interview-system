package com.kun.aiinterview.interview.evaluation.llm.deepseek;

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
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekPropertiesTest {

    private static final String TEST_API_KEY = "unit-test-deepseek-api-key";

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
    void givenCompleteProperties_whenValidate_thenAccepts() {
        assertThat(validator.validate(validProperties())).isEmpty();
    }

    @Test
    void givenNullBaseUrl_whenValidate_thenRejects() {
        DeepSeekProperties properties = validProperties();
        properties.setBaseUrl(null);

        assertViolationOn(properties, "baseUrl");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMissingOrBlankApiKey_whenValidate_thenRejects(String apiKey) {
        DeepSeekProperties properties = validProperties();
        properties.setApiKey(apiKey);

        assertViolationOn(properties, "apiKey");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMissingOrBlankModel_whenValidate_thenRejects(String model) {
        DeepSeekProperties properties = validProperties();
        properties.setModel(model);

        assertViolationOn(properties, "model");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void givenNonPositiveMaxTokens_whenValidate_thenRejects(int maxTokens) {
        DeepSeekProperties properties = validProperties();
        properties.setMaxTokens(maxTokens);

        assertViolationOn(properties, "maxTokens");
    }

    @Test
    void givenNullConnectTimeout_whenValidate_thenRejects() {
        DeepSeekProperties properties = validProperties();
        properties.setConnectTimeout(null);

        assertViolationOn(properties, "connectTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void givenNonPositiveConnectTimeout_whenValidate_thenRejects(long seconds) {
        DeepSeekProperties properties = validProperties();
        properties.setConnectTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "connectTimeoutPositive");
    }

    @Test
    void givenNullReadTimeout_whenValidate_thenRejects() {
        DeepSeekProperties properties = validProperties();
        properties.setReadTimeout(null);

        assertViolationOn(properties, "readTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void givenNonPositiveReadTimeout_whenValidate_thenRejects(long seconds) {
        DeepSeekProperties properties = validProperties();
        properties.setReadTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "readTimeoutPositive");
    }

    @Test
    void toStringDoesNotExposeApiKey() {
        DeepSeekProperties properties = validProperties();

        assertThat(properties.toString())
                .doesNotContain(TEST_API_KEY)
                .doesNotContain("apiKey");
        assertThat(DeepSeekProperties.class).hasAnnotation(Validated.class);
    }

    private static DeepSeekProperties validProperties() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setBaseUrl(URI.create("http://localhost"));
        properties.setApiKey(TEST_API_KEY);
        properties.setModel("test-model");
        properties.setMaxTokens(2048);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static void assertViolationOn(
            DeepSeekProperties properties,
            String propertyPath
    ) {
        Set<ConstraintViolation<DeepSeekProperties>> violations =
                validator.validate(properties);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .contains(propertyPath);
    }
}
