package com.kun.aiinterview.security.jwt;

import com.kun.aiinterview.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenService {
    private static final String CLAIM_ACCOUNT = "account";
    private static final String CLAIM_ROLE = "role";

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

    public String generateAccessToken(Long userId, String account, UserRole role) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plus(
                jwtProperties.getAccessTokenExpiration()
        );

        return Jwts.builder()
                .subject((userId.toString()))
                .claim(CLAIM_ACCOUNT, account)
                .claim(CLAIM_ROLE,role.name())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidate(String token){
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationSeconds(){
        return jwtProperties
                .getAccessTokenExpiration()
                .toSeconds();
    }
}
