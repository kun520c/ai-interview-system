package com.kun.aiinterview.interview.evaluation.judge.jev;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@ToString(exclude = "apiKey")
@ConfigurationProperties(prefix = "ai.judge.jev")
public class JevProperties {
    @NotNull
    private URI baseUrl = URI.create("https://api.typesafe.ai");
    @NotBlank
    private String apiKey;
    @NotBlank
    @Size(max = 100)
    private String model = "jev-latest";
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(20);
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double minConfidence = 0.0;

    @AssertTrue(message = "Jev服务地址必须是绝对HTTP(S)地址")
    public boolean isBaseUrlValid() {
        return baseUrl == null || (baseUrl.getHost() != null
                && baseUrl.getUserInfo() == null
                && ("https".equals(baseUrl.getScheme()) || "http".equals(baseUrl.getScheme())));
    }

    @AssertTrue(message = "Jev超时时间必须大于0")
    public boolean isTimeoutsPositive() {
        return (connectTimeout == null || (!connectTimeout.isZero() && !connectTimeout.isNegative()))
                && (readTimeout == null || (!readTimeout.isZero() && !readTimeout.isNegative()));
    }

    @AssertTrue(message = "Jev confidence阈值必须是有限数值")
    public boolean isConfidenceFinite() {
        return minConfidence == null || Double.isFinite(minConfidence);
    }
}
