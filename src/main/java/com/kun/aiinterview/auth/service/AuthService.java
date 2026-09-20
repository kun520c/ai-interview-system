package com.kun.aiinterview.auth.service;

import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.vo.LoginResponse;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class AuthService {

    static final String DUPLICATE_ACCOUNT_OR_EMAIL = "账号或邮箱已存在";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public void register(@Valid RegisterRequest registerRequest) {
        if (userMapper.getUserByAccount(registerRequest.getAccount()) != null) {
            log.info("Registration rejected: duplicate account");
            throw new BusinessException(DUPLICATE_ACCOUNT_OR_EMAIL);
        }

        if (userMapper.getUserByEmail(registerRequest.getEmail()) != null) {
            log.info("Registration rejected: duplicate email");
            throw new BusinessException(DUPLICATE_ACCOUNT_OR_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(
                registerRequest.getPassword()
        );

        User user = User.builder()
                .account(registerRequest.getAccount())
                .username(registerRequest.getUsername())
                .password(encodedPassword)
                .email(registerRequest.getEmail())
                .role(UserRole.USER)
                .status(UserStatus.ENABLED)
                .build();

        try {
            userMapper.insertUser(user);
        } catch (DuplicateKeyException exception) {
            log.info("Registration rejected: unique constraint conflict");
            throw new BusinessException(DUPLICATE_ACCOUNT_OR_EMAIL);
        }
    }

    public LoginResponse login(@Valid LoginRequest loginRequest) {
        User user = userMapper.getUserByAccount(loginRequest.getAccount());
        if (user == null) {
            throw new BusinessException("账号或密码错误");
        }
        if (!passwordEncoder.matches(loginRequest.getPassword(),user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getAccount(),
                user.getRole(),
                user.getPassword()
        );

        return LoginResponse.builder()
                .userId(user.getId())
                .account(user.getAccount())
                .username(user.getUsername())
                .role(user.getRole())
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(
                        jwtTokenService.getAccessTokenExpirationSeconds()
                )
                .build();
    }
}
