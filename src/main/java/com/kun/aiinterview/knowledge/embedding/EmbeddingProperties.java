package com.kun.aiinterview.knowledge.embedding;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    public static final int MAX_BATCH_SIZE = 20;
    public static final int MAX_MODEL_LENGTH = 100;
    public static final int MAX_PROFILE_VERSION_LENGTH = 50;

    @NotNull(message = "Embedding 服务地址不能为空")
    private URI baseUrl;

    @NotBlank(message = "Embedding API Key 不能为空")
    private String apiKey;

    @NotBlank(message = "Embedding 模型名称不能为空")
    @Size(max = MAX_MODEL_LENGTH, message = "Embedding 模型名称长度不能超过100")
    private String model;

    @Positive(message = "Embedding 向量维度必须大于 0")
    private int dimension;

    @Positive(message = "Embedding 批次大小必须大于 0")
    @Max(value = MAX_BATCH_SIZE, message = "Embedding 批次大小不能超过百炼单次上限 20")
    private int batchSize;

    @NotBlank(message = "Embedding 配置版本不能为空")
    @Size(
            max = MAX_PROFILE_VERSION_LENGTH,
            message = "Embedding 配置版本长度不能超过50"
    )
    private String profileVersion;

    @NotNull(message = "Embedding 连接超时时间不能为空")
    private Duration connectTimeout;

    @NotNull(message = "Embedding 读取超时时间不能为空")
    private Duration readTimeout;

    @AssertTrue(message = "Embedding连接超时时间必须大于0")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout == null
                ||(!connectTimeout.isZero())
                && !connectTimeout.isNegative();
    }

    @AssertTrue(message = "Embedding读取超时时间必须大于0")
    public boolean isReadTimeoutPositive() {
        return readTimeout == null
                ||(!readTimeout.isZero())
                && !readTimeout.isNegative();
    }
}
