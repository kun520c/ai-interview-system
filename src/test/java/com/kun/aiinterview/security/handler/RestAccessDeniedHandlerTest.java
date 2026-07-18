package com.kun.aiinterview.security.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAccessDeniedHandlerTest {

    private static final String EXPECTED_MESSAGE = "权限不足，无法访问资源";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestAccessDeniedHandler accessDeniedHandler =
            new RestAccessDeniedHandler(objectMapper);

    @Test
    void shouldReturnResultJsonWithHttp403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException exception =
                new AccessDeniedException("test access denied");

        accessDeniedHandler.handle(request, response, exception);

        assertEquals(403, response.getStatus());
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        assertTrue(
                MediaType.parseMediaType(response.getContentType())
                        .isCompatibleWith(MediaType.APPLICATION_JSON)
        );

        JsonNode responseBody = objectMapper.readTree(
                response.getContentAsString(StandardCharsets.UTF_8)
        );

        assertEquals(403, responseBody.get("code").asInt());
        assertEquals(EXPECTED_MESSAGE, responseBody.get("message").asText());
        assertTrue(responseBody.get("data").isNull());
    }

    @Test
    void shouldNotExposeAccessDeniedExceptionMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String internalMessage = "ROLE_USER cannot access /api/admin/users";

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException(internalMessage)
        );

        String responseBody = response.getContentAsString(StandardCharsets.UTF_8);

        assertFalse(responseBody.contains(internalMessage));
        assertTrue(responseBody.contains(EXPECTED_MESSAGE));
    }
}
