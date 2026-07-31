package com.kun.aiinterview.knowledge.embedding;

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
@ConfigurationProperties(prefix = "embedding")
@ToString(exclude = "apiKey")
public class EmbeddingProperties {

    @NotNull(message = "Embedding 服务地址不能为空")
    private URI baseUrl;

    @NotBlank(message = "Embedding API Key 不能为空")
    private String apiKey;

    @NotBlank(message = "Embedding 模型名称不能为空")
    private String model;

    @Positive(message = "Embedding 向量维度必须大于 0")
    private int dimension;

    @Positive(message = "Embedding 批次大小必须大于 0")
    private int batchSize;

    @NotBlank(message = "Embedding 配置版本不能为空")
    private String profileVersion;

    @NotNull(message = "Embedding 连接超时时间不能为空")
    private Duration connectTimeout;

    @NotNull(message = "Embedding 读取超时时间不能为空")
    private Duration readTimeout;

    @AssertTrue(message = "Embedding连接超时时间必须大于0")
    public boolean isConnectTimeoutPositive(){
        return connectTimeout == null
                ||(!connectTimeout.isZero())
                && !connectTimeout.isNegative();
    }

    @AssertTrue(message = "Embedding读取超时时间必须大于0")
    public boolean isReadTimeoutPositive(){
        return readTimeout == null
                ||(!readTimeout.isZero())
                && !readTimeout.isNegative();
    }
}
