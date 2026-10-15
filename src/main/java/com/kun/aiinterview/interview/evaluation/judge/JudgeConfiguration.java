package com.kun.aiinterview.interview.evaluation.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevClient;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevJudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevProperties;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "deepseek", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeConfiguration {
    @Bean
    public JudgeProvider judgeProvider(JudgeProperties settings, DeepSeekEvaluationClient deepSeek,
                                      ObjectProvider<JevClient> jev, ObjectProvider<JevProperties> properties,
                                      ObjectMapper mapper, LlmEvaluationValidator validator) {
        var fallback = new DeepSeekJudgeProvider(deepSeek);
        return switch (settings.getProvider()) {
            case DEEPSEEK -> fallback;
            case JEV -> new JevJudgeProvider(jev.getObject(), properties.getObject(),
                    deepSeek, fallback, mapper, validator);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.judge", name = "provider", havingValue = "jev")
    public JevProperties jevProperties() {
        return new JevProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.judge", name = "provider", havingValue = "jev")
    public JevClient jevClient(RestClient.Builder builder, JevProperties properties, ObjectMapper mapper) {
        var httpClient = JevClient.createHttpClient(properties);
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        RestClient restClient = builder.clone().baseUrl(properties.getBaseUrl().toString())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey()).build();
        return new JevClient(restClient, mapper);
    }
}
