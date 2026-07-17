package com.kun.aiinterview.user.mapper;

import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String testUserAccount = "test_mapper_user";
    private final String testUserEmail = "mapper-test@example.com";
    private final String testUserUsername = "Mapper测试用户";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ?",
                testUserAccount
        );

        jdbcTemplate.update(
                """
                INSERT INTO `user`
                (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "test_mapper_user",
                testUserUsername,
                "TEST_HASH_NOT_FOR_AUTH",
                testUserEmail,
                "USER",
                "ENABLED"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ?",
                testUserAccount
        );
    }

    @Test
    void shouldGetUserByAccount() {
        User user = userMapper.getUserByAccount(testUserAccount);

        assertNotNull(user);
        assertEquals(testUserAccount, user.getAccount());
        assertEquals(testUserUsername, user.getUsername());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals(UserStatus.ENABLED, user.getStatus());
    }

    @Test
    void shouldGetUserById() {
        User inserted = userMapper.getUserByAccount(testUserAccount);

        User user = userMapper.getUserById(inserted.getId());

        assertNotNull(user);
        assertEquals(inserted.getId(), user.getId());
    }

    @Test
    void shouldGetUserByEmail() {
        User user = userMapper.getUserByEmail(testUserEmail);
        assertNotNull(user);
        assertEquals(testUserEmail, user.getEmail());
    }
}
