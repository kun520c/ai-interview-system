package com.kun.aiinterview.security.filter;

import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.security.model.AuthenticatedUser;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }

        String requestPath = getRequestPath(request);
        return LOGIN_PATH.equals(requestPath)
                || REGISTER_PATH.equals(requestPath);
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = resolveBearerToken(request);

            if (token != null) {
                authenticate(token);
            }
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            restAuthenticationEntryPoint.commence(
                    request,
                    response,
                    exception
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null) {
            return null;
        }

        if (authorizationHeader.isBlank()) {
            throw new BadCredentialsException(
                    "Authorization Header 不能为空"
            );
        }

        boolean usesBearerScheme = authorizationHeader.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length()
        );

        if (!usesBearerScheme) {
            throw new BadCredentialsException(
                    "Authorization Header 必须使用 Bearer scheme"
            );
        }

        String token = authorizationHeader
                .substring(BEARER_PREFIX.length())
                .trim();

        if (token.isEmpty()) {
            throw new BadCredentialsException("Bearer Token 不能为空");
        }

        return token;
    }

    private void authenticate(String token) {
        Claims claims = parseAndValidateClaims(token);
        Long userId = extractUserId(claims);
        User user = loadEnabledUser(userId);

        validatePasswordChange(claims,user);

        Authentication authentication = createAuthentication(user);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private Claims parseAndValidateClaims(String token) {
        try {
            return jwtTokenService.parseAndValidate(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    "访问令牌无效",
                    exception
            );
        }
    }

    private Long extractUserId(Claims claims) {
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("访问令牌缺少用户标识");
        }

        try {
            Long userId = Long.valueOf(subject);

            if (userId <= 0) {
                throw new BadCredentialsException(
                        "访问令牌中的用户标识不合法"
                );
            }

            return userId;
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException(
                    "访问令牌中的用户标识格式错误",
                    exception
            );
        }
    }

    private User loadEnabledUser(Long userId) {
        User user = userMapper.getUserById(userId);

        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BadCredentialsException("访问令牌对应的用户不可用");
        }

        return user;
    }

    private Authentication createAuthentication(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getAccount(),
                user.getUsername()
        );
        GrantedAuthority authority = mapAuthority(user.getRole());

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(authority)
        );
    }

    private GrantedAuthority mapAuthority(UserRole role) {
        if (role == null) {
            throw new IllegalStateException("数据库用户角色不能为空");
        }

        return new SimpleGrantedAuthority(ROLE_PREFIX + role.name());
    }

    private void validatePasswordChange(
            Claims claims,
            User user
    ) {
        LocalDateTime passwordChangedAt =
                user.getPasswordChangedAt();

        if (passwordChangedAt == null) {
            return;
        }

        Date issuedAt = claims.getIssuedAt();

        if (issuedAt == null) {
            throw new BadCredentialsException(
                    "访问令牌缺少签发时间"
            );
        }

        LocalDateTime tokenIssuedAt =
                LocalDateTime.ofInstant(
                        issuedAt.toInstant(),
                        ZoneId.systemDefault()
                ).withNano(0);

        if (tokenIssuedAt.isBefore(passwordChangedAt)) {
            throw new BadCredentialsException(
                    "访问令牌已因密码修改而失效"
            );
        }
    }
}