package com.kun.aiinterview.interview.evaluation.judge.jev;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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
    @Min(0)
    @Max(1)
    private int feedbackMaxRetries = 1;
    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(80);
    @NotNull
    private Duration feedbackTimeout = Duration.ofSeconds(20);
    @NotNull
    private Duration fallbackTimeout = Duration.ofSeconds(25);
    @Valid
    @NotNull
    private ProxySettings proxy = new ProxySettings();

    @Getter
    @Setter
    @ToString(exclude = "host")
    public static class ProxySettings {
        private boolean enabled;
        private String host;
        private Integer port;

        @AssertTrue(message = "Jev代理启用时必须指定合法host和1至65535的port")
        public boolean isConfigurationValid() {
            if (port != null && (port < 1 || port > 65535)) {
                return false;
            }
            if (!enabled) {
                return true;
            }
            if (host == null || host.isBlank() || port == null
                    || host.chars().anyMatch(Character::isWhitespace)) {
                return false;
            }

            try {
                URI address = URI.create("http://" + host + ":" + port);
                return address.getHost() != null && address.getUserInfo() == null
                        && address.getPort() == port && address.getRawPath().isEmpty()
                        && address.getRawQuery() == null && address.getRawFragment() == null;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

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

    @AssertTrue(message = "Jev预算须为正且总预算不超过180s，反馈和降级不超过60s，总预算须容纳降级与2s反馈窗口")
    public boolean isRequestBudgetValid() {
        if (requestTimeout == null || feedbackTimeout == null || fallbackTimeout == null) {
            return true; // @NotNull reports the missing property.
        }
        return requestTimeout.compareTo(Duration.ZERO) > 0
                && requestTimeout.compareTo(Duration.ofSeconds(180)) <= 0
                && feedbackTimeout.compareTo(Duration.ZERO) > 0
                && feedbackTimeout.compareTo(Duration.ofSeconds(60)) <= 0
                && fallbackTimeout.compareTo(Duration.ZERO) > 0
                && fallbackTimeout.compareTo(Duration.ofSeconds(60)) <= 0
                && requestTimeout.compareTo(fallbackTimeout.plusSeconds(2)) > 0;
    }
}
