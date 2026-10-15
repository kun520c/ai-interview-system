package com.kun.aiinterview.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;

/** Rejects unsafe resolved targets before datasource and Milvus beans are created. */
public class EnvironmentIsolationInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        boolean testRuntime = new ClassPathResource(
                "META-INF/ai-interview-test-runtime", context.getClassLoader()
        ).exists();
        validate(context.getEnvironment(), testRuntime);
    }

    static void validate(ConfigurableEnvironment environment, boolean testRuntime) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean evaluation = profiles.contains("evaluation");
        boolean test = profiles.contains("test") || profiles.contains("test-real");
        if (evaluation && (test || profiles.contains("local"))) {
            throw new IllegalStateException("evaluation must use its own profile, without local/test");
        }
        if (testRuntime && !evaluation && !test) {
            throw new IllegalStateException("Tests must explicitly use test or test-real profile");
        }
        if (!evaluation && !test) {
            return;
        }

        String database = evaluation ? "ai_interview_evaluation" : "ai_interview_test";
        String collection = evaluation
                ? "knowledge_chunk_vectors_evaluation" : "knowledge_chunk_vectors_test";
        Binder binder = Binder.get(environment);
        requireDatabase(binder.bind("spring.datasource.url", String.class).orElse(""), database);
        binder.bind("spring.datasource.hikari.jdbc-url", String.class)
                .ifBound(url -> requireDatabase(url, database));
        binder.bind("spring.datasource.hikari.catalog", String.class)
                .ifBound(catalog -> requireValue(catalog, database, "datasource catalog"));
        binder.bind("spring.datasource.hikari.schema", String.class)
                .ifBound(schema -> requireValue(schema, database, "datasource schema"));
        binder.bind("spring.datasource.hikari.connection-init-sql", String.class)
                .ifBound(sql -> {
                    if (!sql.isBlank()) {
                        throw new IllegalStateException("Isolated profiles reject datasource connection-init-sql");
                    }
                });
        requireValue(binder.bind("milvus.collection-name", String.class).orElse(""),
                collection, "Milvus collection");
    }

    private static void requireDatabase(String jdbcUrl, String expected) {
        try {
            if (!jdbcUrl.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            if (uri.getHost() == null || !("/" + expected).equals(uri.getPath())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            // Do not include a JDBC URL in the error: its parameters could contain secrets.
            throw new IllegalStateException("Isolated profile requires MySQL database " + expected);
        }
    }

    private static void requireValue(String actual, String expected, String kind) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Isolated profile requires " + kind + " " + expected);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
