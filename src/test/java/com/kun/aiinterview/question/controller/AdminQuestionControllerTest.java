package com.kun.aiinterview.question.controller;

import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void shouldReturnUnauthorizedForUpdateWithoutAccessToken() throws Exception {
        mockMvc.perform(put("/api/admin/questions/77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldReturnForbiddenForDatabaseUserUpdatingQuestion()
            throws Exception {
        stubTokenUser("update-user-token", UserRole.USER);

        mockMvc.perform(put("/api/admin/questions/77")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer update-user-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldValidateUpdateRequestForDatabaseAdmin() throws Exception {
        stubTokenUser("update-admin-token", UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/questions/77")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer update-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldPassPathIdAndValidatedRequestToUpdateService()
            throws Exception {
        stubTokenUser("valid-update-admin-token", UserRole.ADMIN);
        ArgumentCaptor<UpdateQuestionRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateQuestionRequest.class);

        mockMvc.perform(put("/api/admin/questions/77")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer valid-update-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.questionContent").doesNotExist());

        verify(questionAdminService).updateQuestion(
                eq(77L),
                requestCaptor.capture()
        );
        UpdateQuestionRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals(QuestionCategory.JVM, request.getCategory()),
                () -> assertEquals("类加载机制", request.getKnowledgePoint()),
                () -> assertEquals(
                        QuestionDifficulty.HARD,
                        request.getDifficulty()
                ),
                () -> assertEquals("请说明类加载过程", request.getQuestionContent()),
                () -> assertEquals("参考答案", request.getReferenceAnswer()),
                () -> assertEquals(2, request.getScoringPoints().size()),
                () -> assertEquals(
                        QuestionPointType.CORE,
                        request.getScoringPoints().get(0).getPointType()
                ),
                () -> assertEquals(
                        60,
                        request.getScoringPoints().get(0).getWeight()
                ),
                () -> assertEquals(
                        QuestionPointType.INTERNAL,
                        request.getScoringPoints().get(1).getPointType()
                ),
                () -> assertEquals(
                        40,
                        request.getScoringPoints().get(1).getWeight()
                )
        );
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

    private String validUpdateJson() {
        return """
                {
                  "category": "JVM",
                  "knowledgePoint": "类加载机制",
                  "difficulty": "HARD",
                  "questionContent": "请说明类加载过程",
                  "referenceAnswer": "参考答案",
                  "scoringPoints": [
                    {
                      "pointType": "CORE",
                      "content": "核心结论",
                      "weight": 60
                    },
                    {
                      "pointType": "INTERNAL",
                      "content": "内部机制",
                      "weight": 40
                    }
                  ]
                }
                """;
    }
}
