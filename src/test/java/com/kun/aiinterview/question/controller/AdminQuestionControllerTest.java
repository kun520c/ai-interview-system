package com.kun.aiinterview.question.controller;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionStatusRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.question.vo.AdminQuestionDetailResponse;
import com.kun.aiinterview.question.vo.AdminScoringPointDetail;
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

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void givenNoToken_whenGettingQuestionDetail_thenReturnsUnifiedUnauthorizedJson()
            throws Exception {
        mockMvc.perform(get("/api/admin/questions/77"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenDatabaseUserRole_whenGettingQuestionDetail_thenReturnsForbidden()
            throws Exception {
        stubTokenUser("detail-user-token", UserRole.USER);

        mockMvc.perform(get("/api/admin/questions/77")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer detail-user-token"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenAdminAndValidId_whenGettingQuestionDetail_thenPassesIdAndReturnsVo()
            throws Exception {
        stubTokenUser("detail-admin-token", UserRole.ADMIN);
        when(questionAdminService.getQuestionScoringPointDetail(77L))
                .thenReturn(AdminQuestionDetailResponse.builder()
                        .id(77L)
                        .category(QuestionCategory.JVM)
                        .knowledgePoint("类加载机制")
                        .difficulty(QuestionDifficulty.HARD)
                        .content("请说明类加载过程")
                        .referenceAnswer("参考答案")
                        .status(QuestionStatus.ENABLED)
                        .scoringPoints(List.of(
                                AdminScoringPointDetail.builder()
                                        .pointType(QuestionPointType.CORE)
                                        .content("核心结论")
                                        .weight(100)
                                        .build()
                        ))
                        .build());

        mockMvc.perform(get("/api/admin/questions/77")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer detail-admin-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.content").value("请说明类加载过程"))
                .andExpect(jsonPath("$.data.scoringPoints[0].pointType")
                        .value("CORE"))
                .andExpect(jsonPath("$.data.scoringPoints[0].content")
                        .value("核心结论"))
                .andExpect(jsonPath("$.data.scoringPoints[0].weight")
                        .value(100))
                .andExpect(jsonPath("$.data.scoringPoints[0].id")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.scoringPoints[0].questionId")
                        .doesNotExist());

        verify(questionAdminService).getQuestionScoringPointDetail(77L);
    }

    @Test
    void givenAdminAndInvalidId_whenGettingQuestionDetail_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("invalid-detail-admin-token", UserRole.ADMIN);
        when(questionAdminService.getQuestionScoringPointDetail(0L))
                .thenThrow(new BusinessException("题目ID不合法"));

        mockMvc.perform(get("/api/admin/questions/0")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-detail-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目ID不合法"));

        verify(questionAdminService).getQuestionScoringPointDetail(0L);
    }

    @Test
    void givenNoToken_whenUpdatingQuestionStatus_thenReturnsUnifiedUnauthorizedJson()
            throws Exception {
        mockMvc.perform(put("/api/admin/questions/77/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenDatabaseUserRole_whenUpdatingQuestionStatus_thenReturnsForbidden()
            throws Exception {
        stubTokenUser("status-user-token", UserRole.USER);

        mockMvc.perform(put("/api/admin/questions/77/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer status-user-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenAdminAndValidStatus_whenUpdatingQuestionStatus_thenPassesPathIdAndDto()
            throws Exception {
        stubTokenUser("status-admin-token", UserRole.ADMIN);
        ArgumentCaptor<UpdateQuestionStatusRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateQuestionStatusRequest.class);

        mockMvc.perform(put("/api/admin/questions/77/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer status-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(questionAdminService).updateQuestionStatus(
                eq(77L),
                requestCaptor.capture()
        );
        assertEquals(
                QuestionStatus.DISABLED,
                requestCaptor.getValue().getStatus()
        );
    }

    @Test
    void givenAdminAndInvalidId_whenUpdatingQuestionStatus_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("invalid-status-id-admin-token", UserRole.ADMIN);
        org.mockito.Mockito.doThrow(
                        new BusinessException("题目ID不合法")
                )
                .when(questionAdminService)
                .updateQuestionStatus(
                        eq(0L),
                        org.mockito.ArgumentMatchers.any(
                                UpdateQuestionStatusRequest.class
                        )
                );

        mockMvc.perform(put("/api/admin/questions/0/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-status-id-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目ID不合法"));
    }

    @Test
    void givenAdminAndMissingStatusBody_whenUpdatingStatus_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("missing-status-body-admin-token", UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/questions/77/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-status-body-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenAdminAndNullStatus_whenUpdatingStatus_thenReturnsValidationError()
            throws Exception {
        stubTokenUser("null-status-admin-token", UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/questions/77/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer null-status-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目状态不能为空"));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void givenAdminAndUnknownStatus_whenUpdatingStatus_thenReturnsParsingError()
            throws Exception {
        stubTokenUser("unknown-status-admin-token", UserRole.ADMIN);

        mockMvc.perform(put("/api/admin/questions/77/status")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer unknown-status-admin-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));

        verifyNoInteractions(questionAdminService);
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
