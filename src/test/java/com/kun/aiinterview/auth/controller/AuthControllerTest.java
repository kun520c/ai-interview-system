package com.kun.aiinterview.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @BeforeEach
    void setUp(){
        jdbcTemplate.update(
                "DELETE FROM `user` WHERE account = ? OR email = ?",
                testUserAccount,
                testUserEmail
        );
    }

    @AfterEach
    void tearDown(){
        jdbcTemplate.update("DELETE FROM `user` WHERE account = ? OR email = ?",
                testUserAccount,
                testUserEmail
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
}
