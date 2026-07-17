package com.kun.aiinterview.security.jwt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer;

    @NotNull
    private Duration accessTokenExpiration;

    @AssertTrue(message = "JWT access token 过期时间必须大于0")
    public boolean isAccessTokenExpirationPositive() {
        return accessTokenExpiration == null
                || (!accessTokenExpiration.isZero())
                && !accessTokenExpiration.isNegative();
    }
}
