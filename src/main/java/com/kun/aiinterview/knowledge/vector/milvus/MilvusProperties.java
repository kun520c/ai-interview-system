package com.kun.aiinterview.knowledge.vector.milvus;

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
@ToString(exclude = "token")
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private boolean enabled = false;

    @NotNull(message = "Milvus URI 不能为空")
    private URI uri;

    private String token;

    @NotBlank(message = "Milvus 数据库名称不能为空")
    private String databaseName;

    @NotBlank(message = "Milvus Collection 名称不能为空")
    private String collectionName;

    @Positive(message = "Milvus 向量维度必须大于 0")
    private int dimension;

    @NotNull(message = "Milvus 连接超时时间不能为空")
    private Duration connectTimeout;

    @NotNull(message = "Milvus 请求超时时间不能为空")
    private Duration requestTimeout;

    @AssertTrue(message = "Milvus连接超时时间必须大于0")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout == null
                ||(!connectTimeout.isZero())
                && !connectTimeout.isNegative();
    }

    @AssertTrue(message = "Milvus请求超时时间必须大于0")
    public boolean isRequestTimeoutPositive(){
        return requestTimeout == null
                ||(!requestTimeout.isZero())
                && !requestTimeout.isNegative();
    }
}
