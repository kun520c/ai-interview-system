package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            DeepSeekConfiguration.class,
                            DeepSeekEvaluationClient.class
                    )
                    .withBean(RestClient.Builder.class, RestClient::builder)
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void givenDeepSeekDisabledWithoutApiKey_whenLoadContext_thenCreatesNoDeepSeekBeans() {
        contextRunner
                .withPropertyValues("deepseek.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(DeepSeekProperties.class);
                    assertThat(context)
                            .doesNotHaveBean(DeepSeekEvaluationClient.class);
                    assertThat(context).doesNotHaveBean(RestClient.class);
                });
    }

    @Test
    void givenDeepSeekEnabledWithValidProperties_whenLoadContext_thenCreatesClient() {
        contextRunner
                .withPropertyValues(
                        "deepseek.enabled=true",
                        "deepseek.base-url=http://localhost",
                        "deepseek.api-key=unit-test-deepseek-api-key",
                        "deepseek.model=test-model",
                        "deepseek.max-tokens=2048",
                        "deepseek.connect-timeout=1s",
                        "deepseek.read-timeout=2s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(DeepSeekProperties.class);
                    assertThat(context)
                            .hasSingleBean(DeepSeekEvaluationClient.class);
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context.getBean(
                            "deepSeekRestClient",
                            RestClient.class
                    )).isNotNull();

                    DeepSeekProperties properties = context.getBean(
                            DeepSeekProperties.class
                    );
                    assertThat(properties.getBaseUrl())
                            .isEqualTo(URI.create("http://localhost"));
                    assertThat(properties.getModel()).isEqualTo("test-model");
                    assertThat(properties.getMaxTokens()).isEqualTo(2048);
                    assertThat(properties.getConnectTimeout())
                            .isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.getReadTimeout())
                            .isEqualTo(Duration.ofSeconds(2));
                });
    }
}
