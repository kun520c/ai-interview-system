package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "deepseek")
@ToString(exclude = "apiKey")
public class DeepSeekProperties {

    @NotNull(message = "DeepSeek服务地址不能为空")
    private URI baseUrl;

    @NotBlank(message = "DeepSeek API Key不能为空")
    private String apiKey;

    @NotBlank(message = "DeepSeek模型不能为空")
    private String model;

    @Positive(message = "DeepSeek maxTokens必须大于0")
    private int maxTokens;

    @NotNull(message = "DeepSeek连接超时时间不能为空")
    private Duration connectTimeout;

    @NotNull(message = "DeepSeek读取超时时间不能为空")
    private Duration readTimeout;

    @AssertTrue(message = "DeepSeek连接超时时间必须大于0")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout == null
                || (!connectTimeout.isZero()
                && !connectTimeout.isNegative());
    }

    @AssertTrue(message = "DeepSeek读取超时时间必须大于0")
    public boolean isReadTimeoutPositive() {
        return readTimeout == null
                || (!readTimeout.isZero()
                && !readTimeout.isNegative());
    }
}
