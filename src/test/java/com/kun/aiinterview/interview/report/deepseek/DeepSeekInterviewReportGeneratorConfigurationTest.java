package com.kun.aiinterview.interview.report.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekConfiguration;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.report.prompt.InterviewReportPromptBuilder;
import com.kun.aiinterview.interview.service.InterviewReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekInterviewReportGeneratorConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            DeepSeekConfiguration.class,
                            DeepSeekEvaluationClient.class,
                            DeepSeekInterviewReportGenerator.class,
                            InterviewReportPromptBuilder.class,
                            EvaluationStandard.class
                    )
                    .withBean(RestClient.Builder.class, RestClient::builder)
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void givenDeepSeekDisabled_whenLoadContext_thenGeneratorBeanIsAbsent() {
        contextRunner
                .withPropertyValues(
                        "deepseek.enabled=false",
                        "milvus.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(
                            DeepSeekInterviewReportGenerator.class
                    );
                    assertThat(context).doesNotHaveBean(
                            InterviewReportGenerator.class
                    );
                });
    }

    @Test
    void givenOnlyDeepSeekEnabled_whenLoadContext_thenGeneratorBeanExists() {
        contextRunner
                .withPropertyValues(
                        "deepseek.enabled=true",
                        "deepseek.base-url=http://localhost",
                        "deepseek.api-key=unit-test-key",
                        "deepseek.model=test-model",
                        "deepseek.max-tokens=2048",
                        "deepseek.connect-timeout=1s",
                        "deepseek.read-timeout=2s",
                        "milvus.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            DeepSeekInterviewReportGenerator.class
                    );
                    assertThat(context).hasSingleBean(
                            InterviewReportGenerator.class
                    );
                });
    }
}
