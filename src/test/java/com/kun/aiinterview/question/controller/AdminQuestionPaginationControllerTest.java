package com.kun.aiinterview.question.controller;

import com.kun.aiinterview.question.dto.QuestionPageQuery;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.question.vo.AdminQuestionListItem;
import com.kun.aiinterview.question.vo.AdminQuestionPageResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminQuestionController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AdminQuestionPaginationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionAdminService questionAdminService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void shouldReturnUnauthorizedForPaginationWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldReturnForbiddenForDatabaseUserRole() throws Exception {
        stubTokenUser("pagination-user-token", UserRole.USER);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-user-token"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldBindDefaultPageValuesBeforeCallingService() throws Exception {
        stubTokenUser("pagination-default-admin", UserRole.ADMIN);
        when(questionAdminService.getQuestionPage(any(QuestionPageQuery.class)))
                .thenReturn(emptyPage(1, 10));
        ArgumentCaptor<QuestionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-default-admin"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(questionAdminService).getQuestionPage(queryCaptor.capture());
        assertAll(
                () -> assertEquals(1, queryCaptor.getValue().getPageNum()),
                () -> assertEquals(10, queryCaptor.getValue().getPageSize())
        );
    }

    @Test
    void shouldTreatExplicitEmptyPageParametersAsDefaults() throws Exception {
        stubTokenUser("pagination-empty-default-admin", UserRole.ADMIN);
        when(questionAdminService.getQuestionPage(any(QuestionPageQuery.class)))
                .thenReturn(emptyPage(1, 10));
        ArgumentCaptor<QuestionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-empty-default-admin"
                        )
                        .param("pageNum", "")
                        .param("pageSize", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(questionAdminService).getQuestionPage(queryCaptor.capture());
        assertAll(
                () -> assertEquals(1, queryCaptor.getValue().getPageNum()),
                () -> assertEquals(10, queryCaptor.getValue().getPageSize())
        );
    }

    @Test
    void shouldBindAllFiltersForDatabaseAdmin() throws Exception {
        stubTokenUser("pagination-filter-admin", UserRole.ADMIN);
        when(questionAdminService.getQuestionPage(any(QuestionPageQuery.class)))
                .thenReturn(emptyPage(2, 20));
        ArgumentCaptor<QuestionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(QuestionPageQuery.class);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-filter-admin"
                        )
                        .param("pageNum", "2")
                        .param("pageSize", "20")
                        .param("category", "JVM")
                        .param("difficulty", "HARD")
                        .param("status", "DISABLED")
                        .param("keyword", " class loading "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(questionAdminService).getQuestionPage(queryCaptor.capture());
        QuestionPageQuery query = queryCaptor.getValue();
        assertAll(
                () -> assertEquals(2, query.getPageNum()),
                () -> assertEquals(20, query.getPageSize()),
                () -> assertEquals(QuestionCategory.JVM, query.getCategory()),
                () -> assertEquals(
                        QuestionDifficulty.HARD,
                        query.getDifficulty()
                ),
                () -> assertEquals(QuestionStatus.DISABLED, query.getStatus()),
                () -> assertEquals(" class loading ", query.getKeyword())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pageNum=0",
            "pageSize=0",
            "pageSize=51"
    })
    void shouldReturnBadRequestForOutOfRangePageParameter(String parameter)
            throws Exception {
        stubTokenUser("pagination-range-admin-" + parameter, UserRole.ADMIN);
        String[] pair = parameter.split("=");

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-range-admin-" + parameter
                        )
                        .param(pair[0], pair[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pageNum=abc",
            "pageSize=abc",
            "category=NOT_EXIST",
            "difficulty=NOT_EXIST",
            "status=NOT_EXIST"
    })
    void shouldReturnBadRequestForQueryTypeMismatch(String parameter)
            throws Exception {
        String token = "pagination-type-admin-" + parameter;
        stubTokenUser(token, UserRole.ADMIN);
        String[] pair = parameter.split("=");

        mockMvc.perform(get("/api/admin/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param(pair[0], pair[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldReturnBadRequestForKeywordLongerThanOneHundredCharacters()
            throws Exception {
        stubTokenUser("pagination-keyword-admin", UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-keyword-admin"
                        )
                        .param("keyword", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(questionAdminService);
    }

    @Test
    void shouldReturnUnifiedPageResponseWithOnlyListItemFields()
            throws Exception {
        stubTokenUser("pagination-response-admin", UserRole.ADMIN);
        AdminQuestionListItem item = AdminQuestionListItem.builder()
                .id(91L)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("How does HashMap work?")
                .status(QuestionStatus.ENABLED)
                .updatedAt(LocalDateTime.of(2026, 7, 24, 12, 0))
                .build();
        AdminQuestionPageResponse response =
                AdminQuestionPageResponse.builder()
                        .records(List.of(item))
                        .total(1L)
                        .pageNum(1)
                        .pageSize(10)
                        .totalPages(1L)
                        .build();
        when(questionAdminService.getQuestionPage(any(QuestionPageQuery.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer pagination-response-admin"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(91))
                .andExpect(jsonPath("$.data.records[0].category")
                        .value("JAVA_COLLECTION"))
                .andExpect(jsonPath("$.data.records[0].knowledgePoint")
                        .value("HashMap"))
                .andExpect(jsonPath("$.data.records[0].difficulty")
                        .value("MEDIUM"))
                .andExpect(jsonPath("$.data.records[0].questionContent")
                        .value("How does HashMap work?"))
                .andExpect(jsonPath("$.data.records[0].status")
                        .value("ENABLED"))
                .andExpect(jsonPath("$.data.records[0].updatedAt").exists())
                .andExpect(jsonPath("$.data.records[0].updateAt").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].referenceAnswer")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.records[0].scoringPoints")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.records[0].createdAt")
                        .doesNotExist());
    }

    private AdminQuestionPageResponse emptyPage(int pageNum, int pageSize) {
        return AdminQuestionPageResponse.builder()
                .records(List.of())
                .total(0L)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .totalPages(0L)
                .build();
    }

    private void stubTokenUser(String token, UserRole role) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
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
