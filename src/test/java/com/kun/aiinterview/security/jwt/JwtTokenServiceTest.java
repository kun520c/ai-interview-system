package com.kun.aiinterview.security.jwt;

import com.kun.aiinterview.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String WRONG_SECRET =
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";
    private static final String TEST_ISSUER = "ai-interview-system-test";
    private static final Duration ACCESS_TOKEN_EXPIRATION = Duration.ofHours(2);
    private static final Long USER_ID = 1001L;
    private static final String ACCOUNT = "jwt_test_user";

    private JwtTokenService jwtTokenService;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = createProperties(TEST_SECRET, TEST_ISSUER);
        jwtTokenService = new JwtTokenService(jwtProperties);
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        String token = jwtTokenService.generateAccessToken(
                USER_ID,
                ACCOUNT,
                UserRole.USER
        );

        Claims claims = jwtTokenService.parseAndValidate(token);

        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals(ACCOUNT, claims.get("account", String.class));
        assertEquals(UserRole.USER.name(), claims.get("role", String.class));
        assertEquals(TEST_ISSUER, claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertEquals(
                ACCESS_TOKEN_EXPIRATION.toSeconds(),
                claims.getExpiration().toInstant().getEpochSecond()
                        - claims.getIssuedAt().toInstant().getEpochSecond()
        );
        assertEquals(
                ACCESS_TOKEN_EXPIRATION.toSeconds(),
                jwtTokenService.getAccessTokenExpirationSeconds()
        );
        assertEquals(
                Set.of("sub", "account", "role", "iss", "iat", "exp"),
                claims.keySet()
        );
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("email"));
        assertFalse(claims.containsKey("username"));
        assertFalse(claims.containsKey("status"));
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("account", ACCOUNT)
                .claim("role", UserRole.USER.name())
                .issuer(TEST_ISSUER)
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(secretKey)
                .compact();

        assertThrows(
                ExpiredJwtException.class,
                () -> jwtTokenService.parseAndValidate(expiredToken)
        );
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtTokenService.generateAccessToken(
                USER_ID,
                ACCOUNT,
                UserRole.USER
        );
        String[] tokenParts = token.split("\\.");
        String signature = tokenParts[2];
        char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
        String tamperedToken = tokenParts[0]
                + "."
                + tokenParts[1]
                + "."
                + replacement
                + signature.substring(1);

        assertThrows(
                SignatureException.class,
                () -> jwtTokenService.parseAndValidate(tamperedToken)
        );
    }

    @Test
    void shouldRejectTokenWhenVerificationKeyIsWrong() {
        String token = jwtTokenService.generateAccessToken(
                USER_ID,
                ACCOUNT,
                UserRole.USER
        );
        JwtTokenService serviceWithWrongKey = new JwtTokenService(
                createProperties(WRONG_SECRET, TEST_ISSUER)
        );

        assertThrows(
                SignatureException.class,
                () -> serviceWithWrongKey.parseAndValidate(token)
        );
    }

    @Test
    void shouldRejectTokenWhenIssuerIsWrong() {
        Instant now = Instant.now();
        String wrongIssuerToken = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("account", ACCOUNT)
                .claim("role", UserRole.USER.name())
                .issuer("unexpected-issuer")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_EXPIRATION)))
                .signWith(secretKey)
                .compact();

        assertThrows(
                IncorrectClaimException.class,
                () -> jwtTokenService.parseAndValidate(wrongIssuerToken)
        );
    }

    private JwtProperties createProperties(String secret, String issuer) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.setIssuer(issuer);
        jwtProperties.setAccessTokenExpiration(ACCESS_TOKEN_EXPIRATION);
        return jwtProperties;
    }
}
