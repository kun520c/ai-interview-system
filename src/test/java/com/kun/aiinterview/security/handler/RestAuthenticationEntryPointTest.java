package com.kun.aiinterview.security.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAuthenticationEntryPointTest {

    private static final String EXPECTED_MESSAGE = "未认证或访问令牌无效";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestAuthenticationEntryPoint authenticationEntryPoint =
            new RestAuthenticationEntryPoint(objectMapper);

    @Test
    void shouldReturnResultJsonWithHttp401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        InsufficientAuthenticationException exception =
                new InsufficientAuthenticationException("test authentication failure");

        authenticationEntryPoint.commence(request, response, exception);

        assertEquals(401, response.getStatus());
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertTrue(
                MediaType.parseMediaType(response.getContentType())
                        .isCompatibleWith(MediaType.APPLICATION_JSON)
        );

        JsonNode responseBody = objectMapper.readTree(
                response.getContentAsString(StandardCharsets.UTF_8)
        );

        assertEquals(401, responseBody.get("code").asInt());
        assertEquals(EXPECTED_MESSAGE, responseBody.get("message").asText());
        assertTrue(responseBody.get("data").isNull());
    }

    @Test
    void shouldNotExposeAuthenticationExceptionMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String internalMessage = "expired token for database user 1001";

        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException(internalMessage)
        );

        String responseBody = response.getContentAsString(StandardCharsets.UTF_8);

        assertFalse(responseBody.contains(internalMessage));
        assertTrue(responseBody.contains(EXPECTED_MESSAGE));
    }
}
