package com.kun.aiinterview.auth.service;

import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUniquenessTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userMapper,
                passwordEncoder,
                jwtTokenService
        );
    }

    @Test
    void shouldUseUnifiedMessageForDuplicateAccount() {
        when(userMapper.getUserByAccount("taken-account"))
                .thenReturn(existingUser());

        assertThatThrownBy(() -> authService.register(request(
                "taken-account",
                "new@example.com"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthService.DUPLICATE_ACCOUNT_OR_EMAIL);

        verify(userMapper, never()).insertUser(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldUseUnifiedMessageForDuplicateEmail() {
        when(userMapper.getUserByAccount("new-account")).thenReturn(null);
        when(userMapper.getUserByEmail("taken@example.com"))
                .thenReturn(existingUser());

        assertThatThrownBy(() -> authService.register(request(
                "new-account",
                "taken@example.com"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthService.DUPLICATE_ACCOUNT_OR_EMAIL);

        verify(userMapper, never()).insertUser(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldUseUnifiedMessageForDuplicateKeyRace() {
        when(userMapper.getUserByAccount("new-account")).thenReturn(null);
        when(userMapper.getUserByEmail("new@example.com")).thenReturn(null);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
        when(userMapper.insertUser(any(User.class)))
                .thenThrow(new DuplicateKeyException("uk_user_account"));

        assertThatThrownBy(() -> authService.register(request(
                "new-account",
                "new@example.com"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthService.DUPLICATE_ACCOUNT_OR_EMAIL);
    }

    private User existingUser() {
        return User.builder()
                .id(1L)
                .account("existing")
                .email("existing@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ENABLED)
                .build();
    }

    private RegisterRequest request(String account, String email) {
        return new RegisterRequest(
                account,
                "注册用户",
                "Password123!",
                email
        );
    }
}
