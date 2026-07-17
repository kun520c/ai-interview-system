package com.kun.aiinterview.security.jwt;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptPositiveAccessTokenExpiration() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(
                createProperties(Duration.ofHours(2))
        );

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldRejectMissingAccessTokenExpiration() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(
                createProperties(null)
        );

        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString()
                                .equals("accessTokenExpiration")
                )
        );
    }

    @Test
    void shouldRejectZeroAccessTokenExpiration() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(
                createProperties(Duration.ZERO)
        );

        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString()
                                .equals("accessTokenExpirationPositive")
                )
        );
    }

    @Test
    void shouldRejectNegativeAccessTokenExpiration() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(
                createProperties(Duration.ofSeconds(-1))
        );

        assertTrue(
                violations.stream().anyMatch(violation ->
                        violation.getPropertyPath().toString()
                                .equals("accessTokenExpirationPositive")
                )
        );
    }

    private JwtProperties createProperties(Duration accessTokenExpiration) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
        jwtProperties.setIssuer("ai-interview-system-test");
        jwtProperties.setAccessTokenExpiration(accessTokenExpiration);
        return jwtProperties;
    }
}
