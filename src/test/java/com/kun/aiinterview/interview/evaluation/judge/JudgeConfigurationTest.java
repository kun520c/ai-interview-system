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
            assertThat(props.toString()).doesNotContain("test-only-secret");
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
}
