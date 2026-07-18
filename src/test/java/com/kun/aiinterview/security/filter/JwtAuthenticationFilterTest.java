package com.kun.aiinterview.security.filter;

import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.security.model.AuthenticatedUser;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token-value";
    private static final Long USER_ID = 1001L;

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RestAuthenticationEntryPoint authenticationEntryPoint =
            mock(RestAuthenticationEntryPoint.class);
    private final JwtAuthenticationFilter jwtAuthenticationFilter =
            new JwtAuthenticationFilter(
                    jwtTokenService,
                    userMapper,
                    authenticationEntryPoint
            );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipPostLoginRequest() {
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(
                request("POST", "/api/auth/login")
        ));
    }

    @Test
    void shouldSkipPostRegistrationRequest() {
        assertTrue(jwtAuthenticationFilter.shouldNotFilter(
                request("POST", "/api/auth/register")
        ));
    }

    @Test
    void shouldNotSkipLoginPathWithDifferentHttpMethod() {
        assertFalse(jwtAuthenticationFilter.shouldNotFilter(
                request("GET", "/api/auth/login")
        ));
    }

    @Test
    void shouldNotSkipSimilarAuthenticationPath() {
        assertFalse(jwtAuthenticationFilter.shouldNotFilter(
                request("POST", "/api/auth/login/history")
        ));
    }

    @Test
    void shouldNotSkipProtectedRequest() {
        assertFalse(jwtAuthenticationFilter.shouldNotFilter(
                request("GET", "/api/interviews/1001")
        ));
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/interviews/1001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenService, userMapper, authenticationEntryPoint);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateEnabledUserWithDatabaseRole() throws Exception {
        Claims claims = claims(USER_ID.toString());
        when(jwtTokenService.parseAndValidate(TOKEN)).thenReturn(claims);
        when(userMapper.getUserById(USER_ID)).thenReturn(
                user(UserRole.USER, UserStatus.ENABLED)
        );
        MockHttpServletRequest request = bearerRequest("Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertNull(authentication.getCredentials());
        assertEquals(
                List.of("ROLE_USER"),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
        AuthenticatedUser principal = assertInstanceOf(
                AuthenticatedUser.class,
                authentication.getPrincipal()
        );
        assertEquals(USER_ID, principal.userId());
        assertEquals("database-account", principal.account());
        assertEquals("数据库用户", principal.username());
        verify(claims, never()).get("role", String.class);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldMapDatabaseAdminRoleToRoleAdmin() throws Exception {
        stubValidUser(UserRole.ADMIN, UserStatus.ENABLED);
        MockHttpServletRequest request = bearerRequest("bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        assertEquals(
                "ROLE_ADMIN",
                authentication.getAuthorities().iterator().next().getAuthority()
        );
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectInvalidJwt() throws Exception {
        when(jwtTokenService.parseAndValidate(TOKEN))
                .thenThrow(new JwtException("invalid token"));

        assertAuthenticationFailure("Bearer " + TOKEN);

        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {
        when(jwtTokenService.parseAndValidate(TOKEN))
                .thenThrow(mock(ExpiredJwtException.class));

        assertAuthenticationFailure("Bearer " + TOKEN);

        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectTamperedJwt() throws Exception {
        when(jwtTokenService.parseAndValidate(TOKEN))
                .thenThrow(mock(SignatureException.class));

        assertAuthenticationFailure("Bearer " + TOKEN);

        verifyNoInteractions(userMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-number", "0", "-1"})
    void shouldRejectInvalidSubject(String subject) throws Exception {
        stubClaims(subject);

        assertAuthenticationFailure("Bearer " + TOKEN);

        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldRejectTokenWhenUserDoesNotExist() throws Exception {
        stubClaims(USER_ID.toString());
        when(userMapper.getUserById(USER_ID)).thenReturn(null);

        assertAuthenticationFailure("Bearer " + TOKEN);
    }

    @Test
    void shouldRejectTokenWhenUserIsDisabled() throws Exception {
        stubValidUser(UserRole.USER, UserStatus.DISABLED);

        assertAuthenticationFailure("Bearer " + TOKEN);
    }

    @Test
    void shouldRejectTokenWhenUserStatusIsMissing() throws Exception {
        stubValidUser(UserRole.USER, null);

        assertAuthenticationFailure("Bearer " + TOKEN);
    }

    @Test
    void shouldPropagateDatabaseFailureAsSystemException() throws Exception {
        RuntimeException databaseException = new RuntimeException("database unavailable");
        stubClaims(USER_ID.toString());
        when(userMapper.getUserById(USER_ID)).thenThrow(databaseException);
        MockHttpServletRequest request = bearerRequest("Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> jwtAuthenticationFilter.doFilterInternal(
                        request,
                        response,
                        filterChain
                )
        );

        assertSame(databaseException, thrown);
        verifyNoInteractions(authenticationEntryPoint);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldPropagateMissingDatabaseRoleAsSystemException() throws Exception {
        stubValidUser(null, UserStatus.ENABLED);
        MockHttpServletRequest request = bearerRequest("Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        assertThrows(
                IllegalStateException.class,
                () -> jwtAuthenticationFilter.doFilterInternal(
                        request,
                        response,
                        filterChain
                )
        );

        verifyNoInteractions(authenticationEntryPoint);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectNonBearerAuthorizationHeader() throws Exception {
        assertAuthenticationFailure("Basic credentials");
        verifyNoInteractions(jwtTokenService, userMapper);
    }

    @Test
    void shouldRejectBlankAuthorizationHeader() throws Exception {
        assertAuthenticationFailure("   ");
        verifyNoInteractions(jwtTokenService, userMapper);
    }

    @Test
    void shouldRejectEmptyBearerToken() throws Exception {
        assertAuthenticationFailure("Bearer    ");
        verifyNoInteractions(jwtTokenService, userMapper);
    }

    @Test
    void shouldIgnoreInvalidOldTokenOnPublicLogin() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer malformed-old-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenService, userMapper, authenticationEntryPoint);
    }

    private void assertAuthenticationFailure(String authorizationHeader)
            throws Exception {
        MockHttpServletRequest request = bearerRequest(authorizationHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(AuthenticationException.class)
        );
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void stubValidUser(UserRole role, UserStatus status) {
        stubClaims(USER_ID.toString());
        when(userMapper.getUserById(USER_ID)).thenReturn(user(role, status));
    }

    private void stubClaims(String subject) {
        Claims tokenClaims = claims(subject);
        when(jwtTokenService.parseAndValidate(TOKEN)).thenReturn(tokenClaims);
    }

    private Claims claims(String subject) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    private User user(UserRole role, UserStatus status) {
        return User.builder()
                .id(USER_ID)
                .account("database-account")
                .username("数据库用户")
                .password("password-hash-must-not-enter-principal")
                .role(role)
                .status(status)
                .build();
    }

    private MockHttpServletRequest bearerRequest(String authorizationHeader) {
        MockHttpServletRequest request = request("GET", "/api/interviews/1001");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        return request;
    }

    private MockHttpServletRequest request(String method, String requestPath) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setServletPath(requestPath);
        request.setRequestURI(requestPath);
        return request;
    }
}
