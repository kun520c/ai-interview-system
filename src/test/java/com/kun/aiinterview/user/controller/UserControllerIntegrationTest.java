package com.kun.aiinterview.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
class UserControllerIntegrationTest {

    private static final String CURRENT_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String WRONG_PASSWORD = "WrongPassword789!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldReturnCurrentUserWhenTokenIsValid() throws Exception {
        User user = createEnabledUser();

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getAccount(),
                user.getRole()
        );

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.account").value(user.getAccount()))
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.createdAt").exists())

                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordChangedAt").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void shouldReturnUnauthorizedWhenChangingPasswordWithoutToken() throws Exception {
        ChangePasswordRequest request = changePasswordRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(
                        put("/api/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectNewPasswordShorterThanEightCharacters() throws Exception {
        User user = createEnabledUser();
        String accessToken = generateAccessToken(user);
        ChangePasswordRequest request = changePasswordRequest(
                CURRENT_PASSWORD,
                "Short1"
        );

        mockMvc.perform(
                        authenticatedPut(accessToken, request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码长度应为8到72位"));
    }

    @Test
    void shouldRejectWrongCurrentPasswordWithoutChangingDatabase() throws Exception {
        User user = createEnabledUser();
        User beforeChange = userMapper.getUserById(user.getId());
        String accessToken = generateAccessToken(user);
        ChangePasswordRequest request = changePasswordRequest(
                WRONG_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(
                        authenticatedPut(accessToken, request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入正确的现存密码"));

        User afterChange = userMapper.getUserById(user.getId());
        assertEquals(beforeChange.getPassword(), afterChange.getPassword());
        assertEquals(
                beforeChange.getPasswordChangedAt(),
                afterChange.getPasswordChangedAt()
        );
    }

    @Test
    void shouldRejectNewPasswordEqualToCurrentPassword() throws Exception {
        User user = createEnabledUser();
        User beforeChange = userMapper.getUserById(user.getId());
        String accessToken = generateAccessToken(user);
        ChangePasswordRequest request = changePasswordRequest(
                CURRENT_PASSWORD,
                CURRENT_PASSWORD
        );

        mockMvc.perform(
                        authenticatedPut(accessToken, request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同"));

        User afterChange = userMapper.getUserById(user.getId());
        assertEquals(beforeChange.getPassword(), afterChange.getPassword());
        assertEquals(
                beforeChange.getPasswordChangedAt(),
                afterChange.getPasswordChangedAt()
        );
    }

    @Test
    void shouldChangePasswordAndUpdatePasswordChangedAt() throws Exception {
        User user = createEnabledUser();
        LocalDateTime originalPasswordChangedAt = LocalDateTime.now()
                .minusDays(1)
                .withNano(0);
        assertEquals(
                1,
                userMapper.updatePassword(
                        user.getId(),
                        user.getPassword(),
                        originalPasswordChangedAt
                )
        );
        String accessToken = generateAccessToken(user);
        ChangePasswordRequest request = changePasswordRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD
        );

        mockMvc.perform(
                        authenticatedPut(accessToken, request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        User changedUser = userMapper.getUserById(user.getId());
        assertNotEquals(NEW_PASSWORD, changedUser.getPassword());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, changedUser.getPassword()));
        assertFalse(passwordEncoder.matches(CURRENT_PASSWORD, changedUser.getPassword()));
        assertNotNull(changedUser.getPasswordChangedAt());
        assertTrue(
                changedUser.getPasswordChangedAt().isAfter(originalPasswordChangedAt)
        );
    }

    @Test
    void shouldRejectTokenIssuedBeforePasswordChangedAt() throws Exception {
        User user = createEnabledUser();
        String oldAccessToken = generateAccessToken(user);
        LocalDateTime tokenIssuedAt = LocalDateTime.ofInstant(
                jwtTokenService.parseAndValidate(oldAccessToken)
                        .getIssuedAt()
                        .toInstant(),
                ZoneId.systemDefault()
        ).withNano(0);
        LocalDateTime passwordChangedAt = tokenIssuedAt.plusSeconds(1);

        assertEquals(
                1,
                userMapper.updatePassword(
                        user.getId(),
                        user.getPassword(),
                        passwordChangedAt
                )
        );

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + oldAccessToken
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证或访问令牌无效"));
    }

    @Test
    void shouldLoginWithNewPasswordAndRejectOldPasswordAfterChange()
            throws Exception {
        User user = createEnabledUser();
        LocalDateTime originalPasswordChangedAt = LocalDateTime.now()
                .minusDays(1)
                .withNano(0);
        assertEquals(
                1,
                userMapper.updatePassword(
                        user.getId(),
                        user.getPassword(),
                        originalPasswordChangedAt
                )
        );
        String oldAccessToken = generateAccessToken(user);

        mockMvc.perform(
                        authenticatedPut(
                                oldAccessToken,
                                changePasswordRequest(
                                        CURRENT_PASSWORD,
                                        NEW_PASSWORD
                                )
                        )
                )
                .andExpect(status().isOk());

        LoginRequest oldPasswordLogin = new LoginRequest(
                user.getAccount(),
                CURRENT_PASSWORD
        );
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        oldPasswordLogin
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));

        LoginRequest newPasswordLogin = new LoginRequest(
                user.getAccount(),
                NEW_PASSWORD
        );
        String loginResponseBody = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        newPasswordLogin
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newAccessToken = objectMapper.readTree(loginResponseBody)
                .path("data")
                .path("accessToken")
                .asText();
        assertFalse(newAccessToken.isBlank());

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + newAccessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(user.getId()));
    }

    private MockHttpServletRequestBuilder authenticatedPut(
            String accessToken,
            ChangePasswordRequest request
    ) throws Exception {
        return put("/api/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private ChangePasswordRequest changePasswordRequest(
            String currentPassword,
            String newPassword
    ) {
        return new ChangePasswordRequest(currentPassword, newPassword);
    }

    private String generateAccessToken(User user) {
        return jwtTokenService.generateAccessToken(
                user.getId(),
                user.getAccount(),
                user.getRole()
        );
    }

    private User createEnabledUser() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        User user = User.builder()
                .account("user_" + suffix)
                .username("测试用户")
                .password(passwordEncoder.encode(CURRENT_PASSWORD))
                .email("user_" + suffix + "@test.com")
                .role(UserRole.USER)
                .status(UserStatus.ENABLED)
                .build();

        userMapper.insertUser(user);

        assertNotNull(user.getId());

        return user;
    }
}
