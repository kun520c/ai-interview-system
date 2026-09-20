package com.kun.aiinterview.user.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 101L;
    private static final String CURRENT_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String CURRENT_HASH = "$2a$10$currentHash";
    private static final String NEW_HASH = "$2a$10$newHash";

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder);
    }

    @Test
    void shouldLockUserRowBeforeVerifyingCurrentPassword() {
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH))
                .thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        when(userMapper.updatePassword(
                eq(USER_ID),
                eq(NEW_HASH),
                any(LocalDateTime.class)
        )).thenReturn(1);

        userService.changePassword(USER_ID, request());

        InOrder order = inOrder(userMapper, passwordEncoder);
        order.verify(userMapper).getUserByIdForUpdate(USER_ID);
        order.verify(passwordEncoder).matches(CURRENT_PASSWORD, CURRENT_HASH);
        order.verify(passwordEncoder).matches(NEW_PASSWORD, CURRENT_HASH);
        order.verify(passwordEncoder).encode(NEW_PASSWORD);
        order.verify(userMapper).updatePassword(
                eq(USER_ID),
                eq(NEW_HASH),
                any(LocalDateTime.class)
        );
        verify(userMapper, never()).getUserById(any());
        ArgumentCaptor<LocalDateTime> changedAt =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userMapper).updatePassword(
                eq(USER_ID),
                eq(NEW_HASH),
                changedAt.capture()
        );
        assertThat(changedAt.getValue().getNano()).isZero();
    }

    @Test
    void shouldRejectMissingUserWithoutUpdatingPassword() {
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户不存在");

        verify(userMapper, never()).updatePassword(any(), any(), any());
        verify(userMapper, never()).getUserById(any());
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectDisabledUserWithoutUpdatingPassword() {
        User disabled = enabledUser();
        disabled.setStatus(UserStatus.DISABLED);
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(disabled);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已被封禁");

        verify(userMapper, never()).updatePassword(any(), any(), any());
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectWrongCurrentPasswordWithoutUpdatingPassword() {
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请输入正确的现存密码");

        verify(userMapper, never()).updatePassword(any(), any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldRejectNewPasswordEqualToCurrentPassword() {
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH))
                .thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("新密码不能与原密码相同");

        verify(userMapper, never()).updatePassword(any(), any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldRejectWhenPasswordUpdateAffectsNoRow() {
        when(userMapper.getUserByIdForUpdate(USER_ID)).thenReturn(enabledUser());
        when(passwordEncoder.matches(CURRENT_PASSWORD, CURRENT_HASH))
                .thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, CURRENT_HASH))
                .thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        when(userMapper.updatePassword(
                eq(USER_ID),
                eq(NEW_HASH),
                any(LocalDateTime.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码修改失败");
    }

    private User enabledUser() {
        return User.builder()
                .id(USER_ID)
                .account("locked-user")
                .username("锁定用户")
                .password(CURRENT_HASH)
                .email("locked@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ENABLED)
                .build();
    }

    private ChangePasswordRequest request() {
        return new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
    }
}
