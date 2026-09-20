package com.kun.aiinterview.knowledge.embedding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.knowledge.embedding.springai.SpringAiEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class EmbeddingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(EmbeddingConfiguration.class)
                    .withPropertyValues(
                            "embedding.base-url=http://localhost:18080/v1",
                            "embedding.api-key=test-api-key",
                            "embedding.model=qwen3.7-text-embedding",
                            "embedding.dimension=1024",
                            "embedding.batch-size=20",
                            "embedding.profile-version="
                                    + "qwen3.7-text-embedding-1024-dense-v1",
                            "embedding.connect-timeout=3s",
                            "embedding.read-timeout=20s"
                    );

    @Test
    void shouldExposeOnlySpringAiImplementationForProjectPort() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmbeddingProperties.class);
            assertThat(context).hasSingleBean(OpenAiApi.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class))
                    .isInstanceOf(OpenAiEmbeddingModel.class);
            assertThat(context).hasSingleBean(EmbeddingClient.class);
            assertThat(context.getBean(EmbeddingClient.class))
                    .isInstanceOf(SpringAiEmbeddingClient.class);
        });
    }

    @Test
    void shouldFailFastWhenBatchSizeExceedsProviderLimit() {
        contextRunner.withPropertyValues("embedding.batch-size=21")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("batchSize");
                });
    }

    @Test
    void shouldAcceptModelLengthAtMysqlPersistenceLimit() {
        contextRunner.withPropertyValues(
                        "embedding.model=" + "m".repeat(100)
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmbeddingProperties.class)
                            .getModel()).hasSize(100);
                });
    }

    @Test
    void shouldFailFastWhenModelExceedsMysqlPersistenceLimit() {
        contextRunner.withPropertyValues(
                        "embedding.model=" + "m".repeat(101)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("model");
                });
    }

    @Test
    void shouldAcceptProfileVersionLengthAtMysqlPersistenceLimit() {
        contextRunner.withPropertyValues(
                        "embedding.profile-version=" + "p".repeat(50)
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmbeddingProperties.class)
                            .getProfileVersion()).hasSize(50);
                });
    }

    @Test
    void shouldFailFastWhenProfileVersionExceedsMysqlPersistenceLimit() {
        contextRunner.withPropertyValues(
                        "embedding.profile-version=" + "p".repeat(51)
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("profileVersion");
                });
    }

    @Test
    void shouldRejectNullPrimitiveVectorValuesOnDedicatedMapperOnly()
            throws Exception {
        ObjectMapper applicationMapper =
                Jackson2ObjectMapperBuilder.json().build();
        ObjectMapper embeddingMapper =
                EmbeddingConfiguration.createEmbeddingObjectMapper();

        assertThat(applicationMapper.isEnabled(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        )).isFalse();
        assertThat(embeddingMapper.isEnabled(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        )).isTrue();
        assertThat(applicationMapper.readValue(
                "[0.1,null,0.3]",
                float[].class
        )).containsExactly(0.1F, 0.0F, 0.3F);
        assertThatThrownBy(() -> embeddingMapper.readValue(
                "[0.1,null,0.3]",
                float[].class
        )).isInstanceOf(Exception.class);
    }

    @Test
    void shouldNotMutateApplicationObjectMapperBean() {
        ObjectMapper applicationMapper =
                Jackson2ObjectMapperBuilder.json().build();

        contextRunner.withBean(ObjectMapper.class, () -> applicationMapper)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ObjectMapper.class))
                            .isSameAs(applicationMapper);
                    assertThat(applicationMapper.isEnabled(
                            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
                    )).isFalse();
                });
    }

    @Test
    void shouldApplyConfiguredTimeoutsToDedicatedRestClient() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder restClientBuilder = spy(RestClient.builder());

        EmbeddingConfiguration.applyEmbeddingTimeouts(
                restClientBuilder,
                properties
        );

        verify(restClientBuilder).requestFactory(any(
                JdkClientHttpRequestFactory.class
        ));
    }
}
