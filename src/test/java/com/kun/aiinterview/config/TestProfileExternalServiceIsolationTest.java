package com.kun.aiinterview.config;

import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import com.kun.aiinterview.interview.service.InterviewWorkflowService;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class TestProfileExternalServiceIsolationTest {

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void localThenTestProfilesShouldKeepDeepSeekAndMilvusDisabled() {
        assertThat(environment.getActiveProfiles())
                .containsExactly("local", "test");
        assertThat(environment.getProperty("deepseek.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("milvus.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.getBeanNamesForType(
                InterviewWorkflowService.class
        )).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(
                EvaluationOrchestrationService.class
        )).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(
                KnowledgeDocumentProcessingService.class
        )).isEmpty();
    }

    @Test
    void testYamlShouldOverrideLocalEnabledFlagsWithoutCreatingWorkflowBeans()
            throws Exception {
        StandardEnvironment isolated = new StandardEnvironment();
        isolated.getPropertySources().addLast(new MapPropertySource(
                "application-local",
                Map.of(
                        "deepseek.enabled", true,
                        "milvus.enabled", true
                )
        ));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(
                "application-test",
                new ClassPathResource("application-test.yaml")
        ).forEach(source -> isolated.getPropertySources().addFirst(source));

        assertThat(isolated.getProperty("deepseek.enabled", Boolean.class))
                .isFalse();
        assertThat(isolated.getProperty("milvus.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.getBeanNamesForType(
                InterviewWorkflowService.class
        )).isEmpty();
    }
}
