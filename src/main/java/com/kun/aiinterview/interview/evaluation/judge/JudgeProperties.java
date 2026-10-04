package com.kun.aiinterview.interview.evaluation.judge;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ai.judge")
public class JudgeProperties {
    @NotNull
    private Provider provider = Provider.DEEPSEEK;

    public enum Provider { DEEPSEEK, JEV }
}
