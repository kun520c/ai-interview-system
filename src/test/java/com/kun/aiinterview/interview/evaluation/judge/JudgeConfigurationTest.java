package com.kun.aiinterview.interview.evaluation.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevClient;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevJudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevProperties;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JudgeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JudgeConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(DeepSeekEvaluationClient.class, () -> mock(DeepSeekEvaluationClient.class))
            .withBean(LlmEvaluationValidator.class, LlmEvaluationValidator::new);

    @Test
    void deepSeekDisabledDoesNotCreateJudgeOrRequireJevKey() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JudgeProvider.class)
                .doesNotHaveBean(JevClient.class).doesNotHaveBean(JevProperties.class));
    }

    @Test
    void defaultsToOriginalDeepSeekWithoutJevConfiguration() {
        runner.withPropertyValues("deepseek.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JudgeProvider.class).doesNotHaveBean(JevClient.class);
            assertThat(context.getBean(JudgeProvider.class)).isInstanceOf(DeepSeekJudgeProvider.class);
        });
    }

    @Test
    void explicitlySelectsJevAndBindsProperties() {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=test-only-secret", "ai.judge.jev.min-confidence=0.7").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JudgeProvider.class).hasSingleBean(JevClient.class);
            assertThat(context.getBean(JudgeProvider.class)).isInstanceOf(JevJudgeProvider.class);
            var props = context.getBean(JevProperties.class);
            assertThat(props.getMinConfidence()).isEqualTo(0.7);
            assertThat(props.getReadTimeout()).hasSeconds(20);
            assertThat(props.getProxy().isEnabled()).isFalse();
            assertThat(props.toString()).doesNotContain("test-only-secret");
        });
    }

    @Test
    void bindsExplicitJevProxyWithoutMutatingSharedRestClientBuilderOrDefaultSelector() {
        var defaultSelector = java.net.ProxySelector.getDefault();
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=test-only-secret", "ai.judge.jev.proxy.enabled=true",
                "ai.judge.jev.proxy.host=127.0.0.1", "ai.judge.jev.proxy.port=7897").run(context -> {
            assertThat(context).hasNotFailed();
            var proxy = context.getBean(JevProperties.class).getProxy();
            assertThat(proxy.isEnabled()).isTrue();
            assertThat(proxy.getHost()).isEqualTo("127.0.0.1");
            assertThat(proxy.getPort()).isEqualTo(7897);
            assertThat(java.net.ProxySelector.getDefault()).isSameAs(defaultSelector);
            var builder = context.getBean(RestClient.Builder.class);
            var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
            var otherClient = builder.baseUrl("http://other-client.test").build();
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://other-client.test/"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist("Authorization"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
            otherClient.get().uri("/").retrieve().toBodilessEntity();
            server.verify();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"ai.judge.jev.proxy.host=", "ai.judge.jev.proxy.host=http://localhost",
            "ai.judge.jev.proxy.host=user:secret@localhost", "ai.judge.jev.proxy.host=localhost/path",
            "ai.judge.jev.proxy.port=", "ai.judge.jev.proxy.port=0", "ai.judge.jev.proxy.port=-1",
            "ai.judge.jev.proxy.port=65536", "ai.judge.jev.proxy.port=not-a-port"})
    void rejectsIncompleteOrInvalidEnabledProxy(String invalidProperty) {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=test-only-secret", "ai.judge.jev.proxy.enabled=true",
                "ai.judge.jev.proxy.host=127.0.0.1", "ai.judge.jev.proxy.port=7897", invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unusedJevProxyDoesNotChangeDeepSeekProviderSelection() {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.jev.proxy.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JevClient.class)
                        .hasSingleBean(JudgeProvider.class));
    }

    @Test
    void disabledProxyAcceptsEmptyHostAndPortFromDefaultYaml() {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=test-only-secret", "ai.judge.jev.proxy.enabled=false",
                "ai.judge.jev.proxy.host=", "ai.judge.jev.proxy.port=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JevProperties.class).getProxy().isEnabled()).isFalse();
            assertThat(context.getBean(JevProperties.class).getProxy().getPort()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"ai.judge.jev.api-key=", "ai.judge.jev.connect-timeout=0s",
            "ai.judge.jev.read-timeout=-1s", "ai.judge.jev.min-confidence=-0.1",
            "ai.judge.jev.min-confidence=1.1", "ai.judge.jev.min-confidence=NaN",
            "ai.judge.jev.base-url=relative-path", "ai.judge.jev.model="})
    void rejectsInvalidJevConfiguration(String invalidProperty) {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=test-only-secret", invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnknownProviderRatherThanSilentlySelectingDeepSeek() {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ai.judge.jev.feedback-max-retries=-1", "ai.judge.jev.feedback-max-retries=2",
            "ai.judge.jev.request-timeout=0s", "ai.judge.jev.request-timeout=181s",
            "ai.judge.jev.feedback-timeout=0s", "ai.judge.jev.feedback-timeout=61s",
            "ai.judge.jev.fallback-timeout=-1s", "ai.judge.jev.fallback-timeout=61s",
            "ai.judge.jev.request-timeout=27s"})
    void rejectsUnboundedRetryOrInvalidRequestBudget(String invalidProperty) {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=offline-key", invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsFeedbackRetryAndRequestBudgetWithoutChangingDefaultProvider() {
        runner.withPropertyValues("deepseek.enabled=true", "ai.judge.provider=jev",
                "ai.judge.jev.api-key=offline-key", "ai.judge.jev.feedback-max-retries=0",
                "ai.judge.jev.request-timeout=70s", "ai.judge.jev.feedback-timeout=15s",
                "ai.judge.jev.fallback-timeout=20s").run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(JevProperties.class);
            assertThat(properties.getFeedbackMaxRetries()).isZero();
            assertThat(properties.getRequestTimeout()).hasSeconds(70);
            assertThat(properties.getFeedbackTimeout()).hasSeconds(15);
            assertThat(properties.getFallbackTimeout()).hasSeconds(20);
        });
    }
}
