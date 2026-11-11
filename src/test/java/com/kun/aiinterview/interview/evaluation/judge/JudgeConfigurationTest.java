package com.kun.aiinterview.interview.evaluation.judge;

import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JudgeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JudgeConfiguration.class);

    @Test
    void disabledOrAbsentFlagStartsWithoutClientOrCredentials() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JudgeProvider.class));
        runner.withPropertyValues("deepseek.enabled=false", "ai.judge.provider=jev")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JudgeProvider.class));
    }

    @Test
    void enabledCreatesOnlyDeepSeekJudge() {
        enabledRunner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JudgeProvider.class);
            assertThat(context.getBean(JudgeProvider.class)).isInstanceOf(DeepSeekJudgeProvider.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"jev", "deepseek", "unknown", ""})
    void obsoleteSelectorCannotActivateAnotherProvider(String selector) {
        enabledRunner().withPropertyValues("ai.judge.provider=" + selector,
                "ai.judge.jev.api-key=", "ai.judge.jev.proxy.enabled=true",
                "ai.judge.jev.proxy.port=invalid").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JudgeProvider.class);
            assertThat(context.getBean(JudgeProvider.class)).isInstanceOf(DeepSeekJudgeProvider.class);
            assertThat(context.getBeanFactory().getBeanDefinitionNames())
                    .noneMatch(name -> name.toLowerCase().contains("jev") || name.equals("judgeProperties"));
        });
    }

    @Test
    void obsoleteEnvironmentSelectorIsIgnored() {
        enabledRunner().withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("legacy-environment", Map.of("AI_JUDGE_PROVIDER", "jev"))))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JudgeProvider.class);
                    assertThat(context.getBean(JudgeProvider.class)).isInstanceOf(DeepSeekJudgeProvider.class);
                });
    }

    private ApplicationContextRunner enabledRunner() {
        return runner.withPropertyValues("deepseek.enabled=true")
                .withBean(DeepSeekEvaluationClient.class, () -> mock(DeepSeekEvaluationClient.class));
    }
}
