package com.kun.aiinterview;

import org.springframework.test.context.ActiveProfiles;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest()
@ActiveProfiles("local")
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ?",
                "test_mapper_user"
        );

        jdbcTemplate.update(
                """
            INSERT INTO `user`
            (account, username, password, email, role, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
                "test_mapper_user",
                "Mapper测试用户",
                "TEST_HASH_NOT_FOR_AUTH",
                "mapper-test@example.com",
                "USER",
                "ENABLED"
        );
    }

    @AfterEach
    void tearDown(){
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ?",
                "test_mapper_user"
                );
    }

    @Test
    void shouldFindUserByAccount(){
        User user = userMapper.getUserByAccount("test_mapper_user");

        assertNotNull(user);
        assertEquals("test_mapper_user", user.getAccount());
        assertEquals("Mapper测试用户", user.getUsername());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals(UserStatus.ENABLED, user.getStatus());
    }

    @Test
    void shouldFindUserById(){
        User inserted = userMapper.getUserByAccount("test_mapper_user");

        User user = userMapper.getUserById(inserted.getId());

        assertNotNull(user);
        assertEquals(inserted.getId(), user.getId());
    }

    @Test
    void shouldFindUserByEmail(){
        User user = userMapper.getUserByEmail("mapper-test@example.com");

        assertNotNull(user);
        assertEquals("mapper-test@example.com", user.getEmail());


    }

}
