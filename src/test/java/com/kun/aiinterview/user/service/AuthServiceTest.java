package com.kun.aiinterview.user.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.user.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
@SpringBootTest
@ActiveProfiles("local")
public class AuthServiceTest {
    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String testUserUsername = "Mapper测试用户";
    private final String testUserPassword = "123456789";

    private static final String EXISTING_ACCOUNT = "auth_test_existing_user";
    private static final String EXISTING_USERNAME = "注册测试用户";
    private static final String EXISTING_EMAIL = "auth-existing@example.com";
    private static final String EXISTING_PASSWORD = "TEST_HASH_NOT_FOR_AUTH";

    private final RegisterRequest registerRequest = new RegisterRequest(EXISTING_ACCOUNT,testUserUsername,testUserPassword,EXISTING_EMAIL);

    @BeforeEach
    void setUp(){
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ? or email = ?",
                EXISTING_ACCOUNT,
                EXISTING_EMAIL
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
    void tearDown(){
        jdbcTemplate.update("DELETE FROM `user` WHERE account = ? or email = ?",
                EXISTING_ACCOUNT,
                EXISTING_EMAIL
        );
    }

    @Test
    void shouldRejectDuplicateAccount(){
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(registerRequest)
        );

        assertEquals("账号已存在", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateEmail(){
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.register(registerRequest)
        );
    }
}
