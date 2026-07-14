package com.kun.aiinterview.auth.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.auth.dto.RegisterRequest;
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

    @BeforeEach
    void setUp(){
        jdbcTemplate.update(
                """
                DELETE FROM `user`
                WHERE account IN (?, ?)
                   OR email IN (?, ?)
                """,
                EXISTING_ACCOUNT,
                NEW_ACCOUNT,
                EXISTING_EMAIL,
                NEW_EMAIL
        );

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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                """
                DELETE FROM `user`
                WHERE account IN (?, ?)
                   OR email IN (?, ?)
                """,
                EXISTING_ACCOUNT,
                NEW_ACCOUNT,
                EXISTING_EMAIL,
                NEW_EMAIL
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

        assertTrue(
                passwordEncoder.matches(
                        EXISTING_PASSWORD,
                        savedUser.getPassword()
                )
        );
    }
}