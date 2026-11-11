package com.kun.aiinterview.interview.evaluation.judge;

import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "deepseek", name = "enabled", havingValue = "true")
public class JudgeConfiguration {
    @Bean
    public JudgeProvider judgeProvider(DeepSeekEvaluationClient deepSeek) {
        return new DeepSeekJudgeProvider(deepSeek);
    }
}
