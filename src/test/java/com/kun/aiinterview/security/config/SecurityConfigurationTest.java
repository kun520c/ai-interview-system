package com.kun.aiinterview.security.config;

import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.security.filter.JwtAuthenticationFilter;
import com.kun.aiinterview.security.handler.RestAccessDeniedHandler;
import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.security.model.AuthenticatedUser;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigurationTest.SecurityTestController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityConfigurationTest.SecurityTestController.class
})
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> filterRegistration;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void shouldRunJwtFilterOnlyThroughSpringSecurityChain() {
        assertFalse(filterRegistration.isEnabled());
    }

    @Test
    void shouldAllowLoginWithInvalidOldToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-old-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verifyNoInteractions(jwtTokenService, userMapper);
    }

    @Test
    void shouldAllowRegistrationWithMalformedOldAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, "Basic old-credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verifyNoInteractions(jwtTokenService, userMapper);
    }

    @Test
    void shouldReturnResultJson401ForOtherUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/security-test/authenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证或访问令牌无效"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void shouldReturn401RatherThan403ForUnauthenticatedAdminRequest() throws Exception {
        mockMvc.perform(get("/api/admin/security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证或访问令牌无效"));
    }

    @Test
    void shouldRestoreAuthenticationForEnabledUser() throws Exception {
        stubTokenUser("valid-user-token", UserRole.USER, UserStatus.ENABLED);

        mockMvc.perform(get("/api/security-test/authenticated")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.account").value("database-account"))
                .andExpect(jsonPath("$.data.username").value("数据库用户"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldReturn403WhenDatabaseUserRoleCannotAccessAdminEndpoint()
            throws Exception {
        stubTokenUser("user-token-with-stale-admin-claim", UserRole.USER, UserStatus.ENABLED);

        mockMvc.perform(get("/api/admin/security-test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer user-token-with-stale-admin-claim"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("权限不足，无法访问资源"));
    }

    @Test
    void shouldAllowDatabaseAdminToAccessAdminEndpoint() throws Exception {
        stubTokenUser("admin-token", UserRole.ADMIN, UserStatus.ENABLED);

        mockMvc.perform(get("/api/admin/security-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnResultJson401ForInvalidJwt() throws Exception {
        when(jwtTokenService.parseAndValidate("invalid-token"))
                .thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get("/api/security-test/authenticated")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证或访问令牌无效"));
    }

    private void stubTokenUser(
            String token,
            UserRole role,
            UserStatus status
    ) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1001");
        when(jwtTokenService.parseAndValidate(token)).thenReturn(claims);
        when(userMapper.getUserById(1001L)).thenReturn(
                User.builder()
                        .id(1001L)
                        .account("database-account")
                        .username("数据库用户")
                        .password("password-hash")
                        .role(role)
                        .status(status)
                        .build()
        );
    }

    @RestController
    public static class SecurityTestController {

        @PostMapping("/api/auth/login")
        public Result<Void> login() {
            return Result.success();
        }

        @PostMapping("/api/auth/register")
        public Result<Void> register() {
            return Result.success();
        }

        @GetMapping("/api/security-test/authenticated")
        public Result<AuthenticatedUser> authenticated(
                @AuthenticationPrincipal AuthenticatedUser authenticatedUser
        ) {
            return Result.success(authenticatedUser);
        }

        @GetMapping("/api/admin/security-test")
        public Result<Void> admin() {
            return Result.success();
        }
    }
}
