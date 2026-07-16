package com.kun.aiinterview.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.service.AuthService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    AuthService authService;

    private final String testUserAccount = "test_mapper_user";
    private final String testUserEmail = "mapper-test@example.com";
    private final String testUserUsername = "Mapper测试用户";
    private final String testUserPassword = "123456789";

    private final String loginUserAccount = "controller_login_enabled_user";
    private final String loginUserEmail = "controller-login-enabled@example.com";
    private final String loginUserUsername = "登录接口测试用户";
    private final String loginUserPassword = "LoginPassword123";

    private final String disabledUserAccount = "controller_login_disabled_user";
    private final String disabledUserEmail = "controller-login-disabled@example.com";
    private final String disabledUserUsername = "禁用登录接口测试用户";
    private final String disabledUserPassword = "DisabledPassword123";


    private final String blankAccount = "";
    private final String longAccount = "a".repeat(51);
    private final String shortPassword = "1234567";
    private final String longPassword = "a".repeat(73);
    private final String wrongPassword = "WrongPassword123";
    private final String missingAccount = "controller_login_missing_user";

    @BeforeEach
    void setUp(){
        cleanUpTestUsers();

        insertLoginUser(
                loginUserAccount,
                loginUserUsername,
                loginUserEmail,
                loginUserPassword,
                "ENABLED"
        );

        insertLoginUser(
                disabledUserAccount,
                disabledUserUsername,
                disabledUserEmail,
                disabledUserPassword,
                "DISABLED"
        );
    }

    @AfterEach
    void tearDown(){
        cleanUpTestUsers();
    }

    private void cleanUpTestUsers() {
        jdbcTemplate.update(
                """
                DELETE FROM `user`
                WHERE account IN (?, ?, ?)
                   OR email IN (?, ?, ?)
                """,
                testUserAccount,
                loginUserAccount,
                disabledUserAccount,
                testUserEmail,
                loginUserEmail,
                disabledUserEmail
        );
    }

    private void insertLoginUser(
            String account,
            String username,
            String email,
            String rawPassword,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `user`
                (account, username, email, password, role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                account,
                username,
                email,
                passwordEncoder.encode(rawPassword),
                "USER",
                status
        );
    }

    @Test
    void shouldRegisterSuccessfully() throws Exception{
        RegisterRequest registerRequest = new RegisterRequest(testUserAccount, testUserUsername,testUserPassword,testUserEmail);
        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        User user = userMapper.getUserByAccount(testUserAccount);

        assertNotNull(user);
        assertEquals(testUserAccount, user.getAccount());
        assertEquals(testUserEmail, user.getEmail());
        assertEquals(testUserUsername, user.getUsername());
        assertTrue(passwordEncoder.matches(testUserPassword, user.getPassword()));
    }

    @Test
    void shouldRegisterWithEmptyPassword() throws Exception{
        RegisterRequest registerRequest = new RegisterRequest(testUserAccount, testUserUsername,null,testUserEmail);
        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"))
                .andExpect(jsonPath("$.data").isEmpty());

        User user = userMapper.getUserByAccount(testUserAccount);

        assertNull(user);
    }

    @Test
    void shouldRegisterRepetitive()throws Exception{
        RegisterRequest registerRequest = new RegisterRequest(testUserAccount, testUserUsername,testUserPassword,testUserEmail);
        authService.register(registerRequest);

        mockMvc.perform(
                post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号已存在"))
                .andExpect(jsonPath("$.data").isEmpty());

        User user = userMapper.getUserByAccount(testUserAccount);

        assertNotNull(user);
        assertEquals(testUserAccount, user.getAccount());
        assertEquals(testUserEmail, user.getEmail());
        assertEquals(testUserUsername, user.getUsername());
        assertTrue(passwordEncoder.matches(testUserPassword, user.getPassword()));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception{
        User user = userMapper.getUserByAccount(loginUserAccount);
        assertNotNull(user);
        assertTrue(passwordEncoder.matches(loginUserPassword, user.getPassword()));

        LoginRequest loginRequest = new LoginRequest(loginUserAccount, loginUserPassword);
        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.account").value(loginUserAccount))
                .andExpect(jsonPath("$.data.username").value(loginUserUsername))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void shouldRejectLoginWhenAccountIsBlank() throws Exception{
        assertLoginFails(
                new LoginRequest(blankAccount, loginUserPassword),
                "账号不能为空"
        );
    }

    @Test
    void shouldRejectLoginWhenAccountIsTooLong() throws Exception{
        assertLoginFails(
                new LoginRequest(longAccount, loginUserPassword),
                "账号长度不能超过50位"
        );
    }

    @Test
    void shouldRejectLoginWhenPasswordIsBlank() throws Exception{
        assertLoginFails(
                new LoginRequest(loginUserAccount, null),
                "密码不能为空"
        );
    }

    @Test
    void shouldRejectLoginWhenPasswordIsTooShort() throws Exception{
        assertLoginFails(
                new LoginRequest(loginUserAccount, shortPassword),
                "密码长度应为8到72位"
        );
    }

    @Test
    void shouldRejectLoginWhenPasswordIsTooLong() throws Exception{
        assertLoginFails(
                new LoginRequest(loginUserAccount, longPassword),
                "密码长度应为8到72位"
        );
    }

    @Test
    void shouldRejectLoginWhenPasswordIsWrong() throws Exception{
        assertLoginFails(
                new LoginRequest(loginUserAccount, wrongPassword),
                "账号或密码错误"
        );
    }

    @Test
    void shouldRejectLoginWhenUserIsDisabled() throws Exception{
        assertLoginFails(
                new LoginRequest(disabledUserAccount, disabledUserPassword),
                "账号已被禁用"
        );
    }

    @Test
    void shouldRejectDisabledUserWithGenericMessageWhenPasswordIsWrong() throws Exception{
        assertLoginFails(
                new LoginRequest(disabledUserAccount, wrongPassword),
                "账号或密码错误"
        );
    }

    @Test
    void shouldRejectLoginWhenUserDoesNotExist() throws Exception{
        assertLoginFails(
                new LoginRequest(missingAccount, loginUserPassword),
                "账号或密码错误"
        );
    }

    private void assertLoginFails(LoginRequest loginRequest, String expectedMessage) throws Exception {
        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
        ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
