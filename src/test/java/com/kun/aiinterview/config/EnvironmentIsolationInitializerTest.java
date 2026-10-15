package com.kun.aiinterview.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentIsolationInitializerTest {

    @ParameterizedTest
    @ValueSource(strings = {"test", "test-real", "evaluation"})
    void acceptsOnlyTheDedicatedTargets(String profile) {
        assertThatCode(() -> EnvironmentIsolationInitializer.validate(target(profile), true))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "test-real", "evaluation"})
    void rejectsFormalLocalDatasourceBeforeCreatingBeans(String profile) {
        StandardEnvironment environment = target(profile);
        override(environment, Map.of("spring.datasource.url",
                "jdbc:mysql://localhost:3306/ai_interview?password=DO_NOT_LOG_THIS"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Isolated profile requires MySQL database")
                .hasMessageNotContaining("DO_NOT_LOG_THIS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "test-real", "evaluation"})
    void rejectsFormalLocalMilvusEvenWhenDisabled(String profile) {
        StandardEnvironment environment = target(profile);
        override(environment, Map.of("milvus.enabled", false,
                "milvus.collection-name", "knowledge_chunk_vectors_local"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Milvus collection");
    }

    @Test
    void rejectsLegacyMilvusEnvironmentVariableOverride() {
        StandardEnvironment environment = target("evaluation");
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "unsafe-environment", Map.of("MILVUS_COLLECTION_NAME", "knowledge_chunk_vectors_local")));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Milvus collection");
    }

    @Test
    void rejectsHikariJdbcUrlOverride() {
        StandardEnvironment environment = target("test");
        override(environment, Map.of("spring.datasource.hikari.jdbc-url",
                "jdbc:mysql://localhost:3306/ai_interview"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsHikariCatalogOverride() {
        StandardEnvironment environment = target("evaluation");
        override(environment, Map.of("spring.datasource.hikari.catalog", "ai_interview"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsHikariSchemaOverride() {
        StandardEnvironment environment = target("test");
        override(environment, Map.of("spring.datasource.hikari.schema", "ai_interview"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsConnectionInitializationSqlThatCouldSwitchTargets() {
        StandardEnvironment environment = target("evaluation");
        override(environment, Map.of("spring.datasource.hikari.connection-init-sql", "USE ai_interview"));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection-init-sql");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "jdbc:h2:mem:test", "jdbc:mysql://localhost/ai_interview_test_extra"})
    void rejectsMissingMalformedOrDifferentSchema(String jdbcUrl) {
        StandardEnvironment environment = target("test");
        override(environment, Map.of("spring.datasource.url", jdbcUrl));
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testClasspathCannotRunOnlyLocalProfile() {
        StandardEnvironment environment = target("test");
        environment.setActiveProfiles("local");
        assertThatThrownBy(() -> new EnvironmentIsolationInitializer().initialize(context(environment)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tests must explicitly use test or test-real");
    }

    @Test
    void productionLocalRuntimeIsUnaffected() {
        StandardEnvironment environment = target("test");
        environment.setActiveProfiles("local");
        assertThatCode(() -> EnvironmentIsolationInitializer.validate(environment, false))
                .doesNotThrowAnyException();
    }

    @Test
    void evaluationCannotMergeLocalOrTestProfiles() {
        StandardEnvironment environment = target("evaluation");
        environment.setActiveProfiles("local", "evaluation");
        assertThatThrownBy(() -> EnvironmentIsolationInitializer.validate(environment, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evaluation must use its own profile");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local/test", "local/test-real", "evaluation"})
    void actualYamlResolutionIgnoresGenericLocalDbUrl(String profiles) {
        StandardEnvironment environment = isolatedEnvironment();
        environment.setActiveProfiles(profiles.split("/"));
        override(environment, Map.of("DB_URL", "jdbc:mysql://localhost:3306/ai_interview",
                "DB_USERNAME", "fixture-user", "DB_PASSWORD", "fixture-password"));
        GenericApplicationContext context = context(environment);
        new ConfigDataApplicationContextInitializer().initialize(context);
        new EnvironmentIsolationInitializer().initialize(context);
        String database = profiles.equals("evaluation") ? "ai_interview_evaluation" : "ai_interview_test";
        String collection = profiles.equals("evaluation")
                ? "knowledge_chunk_vectors_evaluation" : "knowledge_chunk_vectors_test";
        assertThat(Binder.get(environment).bind("spring.datasource.url", String.class).get())
                .contains("/" + database + "?");
        assertThat(Binder.get(environment).bind("milvus.collection-name", String.class).get())
                .isEqualTo(collection);
        // No refresh: no datasource, Milvus initializer, embedding, or LLM beans exist.
        assertThat(context.getBeanFactory().getBeanDefinitionCount()).isZero();
    }

    private StandardEnvironment target(String profile) {
        StandardEnvironment environment = isolatedEnvironment();
        environment.setActiveProfiles(profile);
        boolean evaluation = profile.equals("evaluation");
        override(environment, Map.of("spring.datasource.url", "jdbc:mysql://localhost:3306/"
                        + (evaluation ? "ai_interview_evaluation" : "ai_interview_test"),
                "milvus.collection-name", evaluation
                        ? "knowledge_chunk_vectors_evaluation" : "knowledge_chunk_vectors_test"));
        return environment;
    }

    private StandardEnvironment isolatedEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private void override(StandardEnvironment environment, Map<String, Object> values) {
        environment.getPropertySources().addFirst(new MapPropertySource("override-"
                + environment.getPropertySources().size(), values));
    }

    private GenericApplicationContext context(StandardEnvironment environment) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
