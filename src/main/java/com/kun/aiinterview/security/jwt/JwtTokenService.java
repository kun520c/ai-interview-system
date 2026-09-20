package com.kun.aiinterview.security.jwt;

import com.kun.aiinterview.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

@Component
public class JwtTokenService {
    public static final String CLAIM_CREDENTIAL_VERSION = "credential_version";
    private static final String CLAIM_ACCOUNT = "account";
    private static final String CLAIM_ROLE = "role";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        byte[] decodedKeyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());

        this.secretKey = Keys.hmacShaKeyFor(decodedKeyBytes);

        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build();
    }

    public String generateAccessToken(
            Long userId,
            String account,
            UserRole role,
            String passwordHash
    ) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plus(
                jwtProperties.getAccessTokenExpiration()
        );

        return Jwts.builder()
                .subject((userId.toString()))
                .claim(CLAIM_ACCOUNT, account)
                .claim(CLAIM_ROLE, role.name())
                .claim(
                        CLAIM_CREDENTIAL_VERSION,
                        deriveCredentialVersion(passwordHash)
                )
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    public String deriveCredentialVersion(String passwordHash) {
        if (!StringUtils.hasText(passwordHash)) {
            throw new IllegalArgumentException("password hash cannot be blank");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKey);
            byte[] digest = mac.doFinal(
                    passwordHash.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "failed to derive credential version",
                    exception
            );
        }
    }

    public boolean matchesCredentialVersion(
            String tokenCredentialVersion,
            String passwordHash
    ) {
        if (!StringUtils.hasText(tokenCredentialVersion)
                || !StringUtils.hasText(passwordHash)) {
            return false;
        }

        String expected = deriveCredentialVersion(passwordHash);
        return MessageDigest.isEqual(
                tokenCredentialVersion.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties
                .getAccessTokenExpiration()
                .toSeconds();
    }
}
