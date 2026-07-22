package com.kun.aiinterview.question.controller;

import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.security.config.SecurityConfiguration;
import com.kun.aiinterview.security.filter.JwtAuthenticationFilter;
import com.kun.aiinterview.security.handler.RestAccessDeniedHandler;
import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminQuestionController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AdminQuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminQuestionController adminQuestionController;

    @MockitoBean
    private QuestionAdminService questionAdminService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void shouldReturnUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldReturnForbiddenForDatabaseUserRole() throws Exception {
        stubTokenUser("user-token", UserRole.USER);

        mockMvc.perform(post("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer user-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldValidateRequestForDatabaseAdmin() throws Exception {
        stubTokenUser("admin-token", UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldWrapOnlyQuestionIdInSuccessResponse() {
        CreateQuestionRequest request = new CreateQuestionRequest();
        when(questionAdminService.createQuestion(request)).thenReturn(201L);

        var result = adminQuestionController.createQuestion(request);

        assertEquals(200, result.getCode());
        assertEquals(201L, result.getData().getQuestionId());
    }

    private void stubTokenUser(String token, UserRole role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1001");
        when(jwtTokenService.parseAndValidate(token)).thenReturn(claims);
        when(userMapper.getUserById(1001L)).thenReturn(
                User.builder()
                        .id(1001L)
                        .account("database-account")
                        .username("数据库用户")
                        .role(role)
                        .status(UserStatus.ENABLED)
                        .build()
        );
    }
}
