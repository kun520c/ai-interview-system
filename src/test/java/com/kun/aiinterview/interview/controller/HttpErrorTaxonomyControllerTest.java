package com.kun.aiinterview.interview.controller;

import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.interview.service.InterviewReportService;
import com.kun.aiinterview.interview.service.InterviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        InterviewController.class,
        HttpErrorTaxonomyControllerTest.ErrorTaxonomyProbeController.class
})
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        HttpErrorTaxonomyControllerTest.ErrorTaxonomyProbeController.class
})
class HttpErrorTaxonomyControllerTest {

    private static final long USER_ID = 1001L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private InterviewReportService interviewReportService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void shouldReturn401ForAnonymousProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/interviews/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证或访问令牌无效"));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldReturn403WhenUserCallsAdminEndpoint() throws Exception {
        stubTokenUser("user-token", UserRole.USER);

        mockMvc.perform(get("/api/admin/error-taxonomy-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("权限不足，无法访问资源"));
    }

    @Test
    void shouldReturn400ForInvalidPathVariableType() throws Exception {
        stubTokenUser("type-mismatch-token", UserRole.USER);

        mockMvc.perform(get("/api/interviews/abc")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer type-mismatch-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void shouldReturn400ForMissingRequiredRequestParameter() throws Exception {
        stubTokenUser("missing-param-token", UserRole.USER);

        mockMvc.perform(get("/api/error-taxonomy-probe/required")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-param-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必要的请求参数"));
    }

    @Test
    void shouldReturn404ForUnknownApiRoute() throws Exception {
        stubTokenUser("unknown-route-token", UserRole.USER);

        mockMvc.perform(get("/api/no-such-error-taxonomy-route")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer unknown-route-token"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldReturn405ForWrongHttpMethod() throws Exception {
        stubTokenUser("wrong-method-token", UserRole.USER);

        mockMvc.perform(put("/api/interviews/current")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer wrong-method-token"
                        ))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("不支持的请求方法"));
    }

    @Test
    void shouldReturnNonDisclosing404ForMissingOwnedResource() throws Exception {
        stubTokenUser("missing-session-token", UserRole.USER);
        when(interviewService.getDetail(USER_ID, 21L))
                .thenThrow(new ResourceNotFoundException("面试会话不存在"));

        mockMvc.perform(get("/api/interviews/21")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-session-token"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("面试会话不存在"))
                .andExpect(jsonPath("$.message", not(containsString("其他用户"))))
                .andExpect(jsonPath("$.message", not(containsString("不属于"))));

        verify(interviewService).getDetail(USER_ID, 21L);
    }

    @Test
    void shouldReturnSame404ForAnotherUsersOwnedResource() throws Exception {
        stubTokenUser("other-owner-token", UserRole.USER);
        when(interviewService.getDetail(USER_ID, 21L))
                .thenThrow(new ResourceNotFoundException("面试会话不存在"));

        mockMvc.perform(get("/api/interviews/21")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer other-owner-token"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("面试会话不存在"))
                .andExpect(jsonPath("$.message", not(containsString("属于"))));
    }

    @Test
    void shouldReturn409WhenAnswerIsEvaluating() throws Exception {
        stubTokenUser("evaluating-token", UserRole.USER);
        when(interviewService.submitAnswer(any(), any(), any()))
                .thenThrow(new ConflictException("答案正在评估中，请稍后重试"));

        mockMvc.perform(post("/api/interviews/21/answers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer evaluating-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interviewQuestionId": 31,
                                  "answerContent": "数组",
                                  "requestId": "request-31"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("答案正在评估中，请稍后重试"));
    }

    @Test
    void shouldReturn409WhenLostEvaluationClaimConflicts() throws Exception {
        stubTokenUser("lost-claim-token", UserRole.USER);
        when(interviewService.submitAnswer(any(), any(), any()))
                .thenThrow(new ConflictException("答案正在评估中，请稍后重试"));

        mockMvc.perform(post("/api/interviews/21/answers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer lost-claim-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interviewQuestionId": 31,
                                  "answerContent": "数组",
                                  "requestId": "request-31"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("答案正在评估中，请稍后重试"));
    }

    @Test
    void shouldReturn409WhenIdempotentPayloadConflicts() throws Exception {
        stubTokenUser("payload-conflict-token", UserRole.USER);
        when(interviewService.submitAnswer(any(), any(), any()))
                .thenThrow(new ConflictException("requestId已用于不同的答案内容"));

        mockMvc.perform(post("/api/interviews/21/answers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer payload-conflict-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interviewQuestionId": 31,
                                  "answerContent": "另一份答案",
                                  "requestId": "request-31"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("requestId已用于不同的答案内容"));
    }

    @Test
    void shouldReturn409WhenReportIsGenerating() throws Exception {
        stubTokenUser("generating-token", UserRole.USER);
        when(interviewReportService.generateReportForApi(USER_ID, 21L))
                .thenThrow(new ConflictException("报告正在生成中，请稍后重试"));

        mockMvc.perform(post("/api/interviews/21/report")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer generating-token"
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("报告正在生成中，请稍后重试"));
    }

    @Test
    void shouldSanitizeExternalServiceExceptionAs502() throws Exception {
        stubTokenUser("external-token", UserRole.USER);
        when(interviewService.getDetail(USER_ID, 21L))
                .thenThrow(new ExternalServiceException(
                        "DashScope 401 Authorization=Bearer sk-secret raw body"
                ));

        String body = mockMvc.perform(get("/api/interviews/21")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer external-token"
                        ))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("外部服务暂时不可用"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("sk-secret")
                .doesNotContain("Authorization")
                .doesNotContain("raw body")
                .doesNotContain("DashScope");
    }

    @Test
    void shouldMapUnexpectedRuntimeExceptionTo500WithoutLeakingDetail()
            throws Exception {
        stubTokenUser("boom-token", UserRole.USER);
        when(interviewService.getDetail(USER_ID, 21L))
                .thenThrow(new IllegalStateException("internal boom stack"));

        String body = mockMvc.perform(get("/api/interviews/21")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer boom-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统异常，请稍后重试"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("internal boom")
                .doesNotContain("IllegalStateException");
    }

    private void stubTokenUser(String token, UserRole role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(USER_ID));
        when(jwtTokenService.parseAndValidate(token)).thenReturn(claims);
        when(jwtTokenService.matchesCredentialVersion(any(), any()))
                .thenReturn(true);
        when(userMapper.getUserById(USER_ID)).thenReturn(
                User.builder()
                        .id(USER_ID)
                        .account("taxonomy-user")
                        .username("Taxonomy")
                        .password("password-hash")
                        .role(role)
                        .status(UserStatus.ENABLED)
                        .build()
        );
    }

    @RestController
    public static class ErrorTaxonomyProbeController {

        @GetMapping("/api/error-taxonomy-probe/required")
        Result<Void> required(@RequestParam String q) {
            return Result.success();
        }

        @GetMapping("/api/admin/error-taxonomy-probe")
        Result<Void> adminProbe() {
            return Result.success();
        }
    }
}
