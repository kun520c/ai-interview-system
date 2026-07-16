package com.kun.aiinterview.auth.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.auth.dto.LoginRequest;
import com.kun.aiinterview.auth.dto.RegisterRequest;
import com.kun.aiinterview.auth.vo.LoginResponse;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
public class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String EXISTING_ACCOUNT = "auth_test_existing_user";
    private static final String EXISTING_USERNAME = "注册测试用户";
    private static final String EXISTING_EMAIL = "auth-existing@example.com";
    private static final String EXISTING_PASSWORD = "TEST_HASH_NOT_FOR_AUTH";

    private static final String TEST_ACCOUNT = "test_account";
    private static final String TEST_EMAIL = "test-email@example.com";
    private static final String NEW_ACCOUNT = "new_account";
    private static final String NEW_EMAIL = "new-email@example.com";

    private static final String LOGIN_ACCOUNT = "auth_login_enabled_user";
    private static final String LOGIN_USERNAME = "登录测试用户";
    private static final String LOGIN_EMAIL = "auth-login-enabled@example.com";
    private static final String LOGIN_PASSWORD = "LoginPassword123";

    private static final String DISABLED_LOGIN_ACCOUNT = "auth_login_disabled_user";
    private static final String DISABLED_LOGIN_USERNAME = "禁用登录测试用户";
    private static final String DISABLED_LOGIN_EMAIL = "auth-login-disabled@example.com";
    private static final String DISABLED_LOGIN_PASSWORD = "DisabledPassword123";

    private static final String WRONG_PASSWORD = "WrongPassword123";
    private static final String MISSING_ACCOUNT = "auth_login_missing_user";

    @BeforeEach
    void setUp(){
        cleanUpTestUsers();

        jdbcTemplate.update(
                """
                    INSERT INTO `user`
                    (account,username,email,password,role,status)
                    values (?,?,?,?,?,?)
                  """,
                EXISTING_ACCOUNT,
                EXISTING_USERNAME,
                EXISTING_EMAIL,
                EXISTING_PASSWORD,
                "USER",
                "ENABLED"
        );

        insertLoginUser(
                LOGIN_ACCOUNT,
                LOGIN_USERNAME,
                LOGIN_EMAIL,
                LOGIN_PASSWORD,
                UserStatus.ENABLED
        );

        insertLoginUser(
                DISABLED_LOGIN_ACCOUNT,
                DISABLED_LOGIN_USERNAME,
                DISABLED_LOGIN_EMAIL,
                DISABLED_LOGIN_PASSWORD,
                UserStatus.DISABLED
        );
    }

    @AfterEach
    void tearDown() {
        cleanUpTestUsers();
    }

    private void cleanUpTestUsers() {
        jdbcTemplate.update(
                """
                DELETE FROM `user`
                WHERE account IN (?, ?, ?, ?)
                   OR email IN (?, ?, ?, ?)
                """,
                EXISTING_ACCOUNT,
                NEW_ACCOUNT,
                LOGIN_ACCOUNT,
                DISABLED_LOGIN_ACCOUNT,
                EXISTING_EMAIL,
                NEW_EMAIL,
                LOGIN_EMAIL,
                DISABLED_LOGIN_EMAIL
        );
    }

    private void insertLoginUser(
            String account,
            String username,
            String email,
            String rawPassword,
            UserStatus status
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
                UserRole.USER.name(),
                status.name()
        );
    }

    @Test
    void shouldRejectDuplicateAccount(){
        RegisterRequest registerRequest = new RegisterRequest(EXISTING_ACCOUNT,EXISTING_USERNAME,EXISTING_PASSWORD,TEST_EMAIL);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(registerRequest)
        );

        assertEquals("账号已存在", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateEmail(){
        RegisterRequest registerRequest = new RegisterRequest(TEST_ACCOUNT,EXISTING_USERNAME,EXISTING_PASSWORD,EXISTING_EMAIL);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(registerRequest)
        );

        assertEquals("邮箱已被注册", exception.getMessage());
    }

    @Test
    void shouldRegisterUserSuccessfully(){
        RegisterRequest registerRequest = new RegisterRequest(
                NEW_ACCOUNT,
                EXISTING_USERNAME,
                EXISTING_PASSWORD,
                NEW_EMAIL
        );

        authService.register(registerRequest);

        User savedUser = userMapper.getUserByAccount(NEW_ACCOUNT);

        assertNotNull(savedUser);

        assertEquals(EXISTING_USERNAME, savedUser.getUsername());
        assertEquals(NEW_EMAIL, savedUser.getEmail());
        assertEquals(NEW_ACCOUNT, savedUser.getAccount());
        assertNotEquals(EXISTING_PASSWORD, savedUser.getPassword());

        assertEquals(UserRole.USER, savedUser.getRole());
        assertEquals(UserStatus.ENABLED, savedUser.getStatus());

        assertTrue(passwordEncoder.matches(EXISTING_PASSWORD, savedUser.getPassword()));
    }

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest loginRequest = new LoginRequest(LOGIN_ACCOUNT, LOGIN_PASSWORD);

        LoginResponse loginResponse = authService.login(loginRequest);
        User savedUser = userMapper.getUserByAccount(LOGIN_ACCOUNT);

        assertNotNull(loginResponse);
        assertNotNull(savedUser);
        assertTrue(passwordEncoder.matches(LOGIN_PASSWORD, savedUser.getPassword()));
        assertEquals(savedUser.getId(), loginResponse.getUserId());
        assertEquals(LOGIN_ACCOUNT, loginResponse.getAccount());
        assertEquals(LOGIN_USERNAME, loginResponse.getUsername());
        assertEquals(UserRole.USER, loginResponse.getRole());
    }

    @Test
    void shouldRejectLoginWhenUserDoesNotExist() {
        LoginRequest loginRequest = new LoginRequest(MISSING_ACCOUNT, LOGIN_PASSWORD);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }

    @Test
    void shouldRejectLoginWhenPasswordIsWrong() {
        LoginRequest loginRequest = new LoginRequest(LOGIN_ACCOUNT, WRONG_PASSWORD);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }

    @Test
    void shouldRejectLoginWhenUserIsDisabled() {
        LoginRequest loginRequest = new LoginRequest(DISABLED_LOGIN_ACCOUNT, DISABLED_LOGIN_PASSWORD);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("账号已被禁用", exception.getMessage());
    }

    @Test
    void shouldRejectDisabledUserWithGenericMessageWhenPasswordIsWrong() {
        LoginRequest loginRequest = new LoginRequest(DISABLED_LOGIN_ACCOUNT, WRONG_PASSWORD);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("账号或密码错误", exception.getMessage());
    }
}
