package com.kun.aiinterview.auth;

import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.service.AuthService;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.config.PasswordConfig;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.mapper.UserMapper;
import com.kun.aiinterview.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordUtf8BoundaryTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest(name = "DTO accepts {0}")
    @EnumSource(value = PasswordSample.class, names = {"ASCII_MIN", "ASCII_72", "CJK_72", "EMOJI_72", "COMBINING_72", "SPACES", "UNICODE_SPACES"})
    void shouldAcceptValidDtoInputs(PasswordSample sample) {
        assertTrue(VALIDATOR.validate(registration(sample.value)).isEmpty());
        assertTrue(VALIDATOR.validate(new LoginRequest("boundary", sample.value)).isEmpty());
        assertTrue(VALIDATOR.validate(new ChangePasswordRequest(sample.value, sample.value)).isEmpty());
    }

    @ParameterizedTest(name = "DTO rejects {0}")
    @EnumSource(value = PasswordSample.class, names = {"ASCII_73", "MIXED_73", "CJK_75", "EMOJI_76", "COMBINING_75", "EMPTY", "BLANK", "CONTROL_BLANK", "SHORT", "NULL"})
    void shouldRejectInvalidDtoInputs(PasswordSample sample) {
        assertFalse(VALIDATOR.validate(registration(sample.value)).isEmpty());
        assertFalse(VALIDATOR.validate(new LoginRequest("boundary", sample.value)).isEmpty());
        assertFalse(VALIDATOR.validate(new ChangePasswordRequest(PasswordSample.INTERIM.value, sample.value)).isEmpty());
        boolean currentValid = VALIDATOR.validate(new ChangePasswordRequest(sample.value, PasswordSample.INTERIM.value)).isEmpty();
        assertEquals(sample == PasswordSample.SHORT, currentValid);
    }

    @ParameterizedTest(name = "unproxied service rejects {0}")
    @EnumSource(value = PasswordSample.class, names = {"ASCII_73", "MIXED_73", "CJK_75", "EMOJI_76", "COMBINING_75", "EMPTY", "BLANK", "CONTROL_BLANK", "SHORT", "NULL"})
    void shouldProtectServiceCallsWithoutDtoValidation(PasswordSample sample) {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtTokenService tokens = mock(JwtTokenService.class);
        AuthService auth = new AuthService(mapper, encoder, tokens);
        UserService users = new UserService(mapper, encoder);
        assertThrows(BusinessException.class, () -> auth.register(registration(sample.value)));
        assertThrows(BusinessException.class, () -> auth.login(new LoginRequest("boundary", sample.value)));
        assertThrows(BusinessException.class, () -> users.changePassword(1L,
                new ChangePasswordRequest(PasswordSample.INTERIM.value, sample.value)));
        if (sample != PasswordSample.SHORT) {
            assertThrows(BusinessException.class, () -> users.changePassword(1L,
                    new ChangePasswordRequest(sample.value, PasswordSample.INTERIM.value)));
        }
        verifyNoInteractions(mapper, encoder, tokens);
    }

    @Test
    void shouldCharacterizeActualEncoderAndByteFixtures() {
        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
        assertEquals(72, PasswordSample.CJK_72.value.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(73, PasswordSample.MIXED_73.value.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(72, PasswordSample.EMOJI_72.value.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(72, PasswordSample.COMBINING_72.value.getBytes(StandardCharsets.UTF_8).length);
        String hash = encoder.encode(PasswordSample.CJK_72.value);
        assertTrue(encoder.matches(PasswordSample.CJK_72.value, hash));
        assertTrue(encoder.matches(PasswordSample.CJK_75.value, hash));
        assertFalse(encoder.matches(PasswordSample.EMOJI_76.value, hash));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(PasswordSample.CJK_75.value));
    }

    @Test
    void shouldKeepPasswordsOutOfDtoText() {
        String sample = PasswordSample.INTERIM.value;
        assertFalse(registration(sample).toString().contains(sample));
        assertFalse(new LoginRequest("boundary", sample).toString().contains(sample));
        assertFalse(new ChangePasswordRequest(sample, sample).toString().contains(sample));
    }

    private RegisterRequest registration(String password) {
        return new RegisterRequest("boundary", "boundary-test", password, "boundary@example.com");
    }
}
