package com.kun.aiinterview.security.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class AuthenticatedUserTest {

    @Test
    void shouldExposeAuthenticatedUserIdentity(){
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1001L,"TEST_ACCOUNT","测试用户");

        assertEquals(1001L, authenticatedUser.userId());
        assertEquals("TEST_ACCOUNT", authenticatedUser.account());
        assertEquals("测试用户", authenticatedUser.username());
        assertEquals("TEST_ACCOUNT", authenticatedUser.getName());
    }
}
