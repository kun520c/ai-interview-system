package com.kun.aiinterview.knowledge.vector.milvus;

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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusPropertiesTest {

    private static final String TEST_TOKEN = "unit-test-milvus-token";

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
    void shouldBindCompleteValidConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "milvus.enabled=true",
                        "milvus.uri=http://localhost:19530",
                        "milvus.token=" + TEST_TOKEN,
                        "milvus.database-name=test_database",
                        "milvus.collection-name=test_collection",
                        "milvus.dimension=3",
                        "milvus.connect-timeout=1500ms",
                        "milvus.request-timeout=2500ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MilvusProperties.class);

                    MilvusProperties properties = context.getBean(
                            MilvusProperties.class
                    );
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getUri())
                            .isEqualTo(URI.create("http://localhost:19530"));
                    assertThat(properties.getToken()).isEqualTo(TEST_TOKEN);
                    assertThat(properties.getDatabaseName())
                            .isEqualTo("test_database");
                    assertThat(properties.getCollectionName())
                            .isEqualTo("test_collection");
                    assertThat(properties.getDimension()).isEqualTo(3);
                    assertThat(properties.getConnectTimeout())
                            .isEqualTo(Duration.ofMillis(1500));
                    assertThat(properties.getRequestTimeout())
                            .isEqualTo(Duration.ofMillis(2500));
                });
    }

    @Test
    void shouldDefaultEnabledToFalse() {
        assertThat(new MilvusProperties().isEnabled()).isFalse();
    }

    @Test
    void shouldAcceptCompleteValidPropertiesAndEmptyToken() {
        MilvusProperties properties = validProperties();
        properties.setToken("");

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void shouldRegisterValidatedConfigurationProperties() {
        EnableConfigurationProperties registration =
                MilvusConfiguration.class.getAnnotation(
                        EnableConfigurationProperties.class
                );

        assertThat(MilvusProperties.class).hasAnnotation(Validated.class);
        assertThat(registration).isNotNull();
        assertThat(registration.value())
                .containsExactly(MilvusProperties.class);
    }

    @Test
    void shouldRejectMissingUri() {
        MilvusProperties properties = validProperties();
        properties.setUri(null);

        assertViolationOn(properties, "uri");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankDatabaseName(String databaseName) {
        MilvusProperties properties = validProperties();
        properties.setDatabaseName(databaseName);

        assertViolationOn(properties, "databaseName");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankCollectionName(String collectionName) {
        MilvusProperties properties = validProperties();
        properties.setCollectionName(collectionName);

        assertViolationOn(properties, "collectionName");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveDimension(int dimension) {
        MilvusProperties properties = validProperties();
        properties.setDimension(dimension);

        assertViolationOn(properties, "dimension");
    }

    @Test
    void shouldRejectMissingConnectTimeout() {
        MilvusProperties properties = validProperties();
        properties.setConnectTimeout(null);

        assertViolationOn(properties, "connectTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveConnectTimeout(long seconds) {
        MilvusProperties properties = validProperties();
        properties.setConnectTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "connectTimeoutPositive");
    }

    @Test
    void shouldRejectMissingRequestTimeout() {
        MilvusProperties properties = validProperties();
        properties.setRequestTimeout(null);

        assertViolationOn(properties, "requestTimeout");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveRequestTimeout(long seconds) {
        MilvusProperties properties = validProperties();
        properties.setRequestTimeout(Duration.ofSeconds(seconds));

        assertViolationOn(properties, "requestTimeoutPositive");
    }

    @Test
    void shouldNotExposeTokenThroughToString() {
        MilvusProperties properties = validProperties();

        assertThat(properties.toString())
                .doesNotContain(TEST_TOKEN)
                .doesNotContain("token");
    }

    private static MilvusProperties validProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setUri(URI.create("http://localhost:19530"));
        properties.setToken(TEST_TOKEN);
        properties.setDatabaseName("default");
        properties.setCollectionName("knowledge_chunk_vectors");
        properties.setDimension(3);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static void assertViolationOn(
            MilvusProperties properties,
            String propertyPath
    ) {
        Set<ConstraintViolation<MilvusProperties>> violations =
                validator.validate(properties);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .contains(propertyPath);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MilvusProperties.class)
    static class PropertiesConfiguration {
    }
}
