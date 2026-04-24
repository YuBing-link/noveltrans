package com.yumu.noveltranslator.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter 单元测试")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @Mock
    private DecodedJWT decodedJWT;

    @Mock
    private Claim emailClaim;

    @Mock
    private Claim userIdClaim;

    @Mock
    private Claim tenantIdClaim;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        // 通过反射注入 mock
        try {
            var jwtUtilsField = JwtAuthenticationFilter.class.getDeclaredField("jwtUtils");
            jwtUtilsField.setAccessible(true);
            jwtUtilsField.set(filter, jwtUtils);

            var userMapperField = JwtAuthenticationFilter.class.getDeclaredField("userMapper");
            userMapperField.setAccessible(true);
            userMapperField.set(filter, userMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("白名单路径测试")
    class ExcludedPathTests {

        @Test
        @DisplayName("登录路径跳过认证")
        void 登录路径跳过认证() throws Exception {
            when(request.getRequestURI()).thenReturn("/user/login");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }

        @Test
        @DisplayName("health路径跳过认证")
        void health路径跳过认证() throws Exception {
            when(request.getRequestURI()).thenReturn("/health");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }

        @Test
        @DisplayName("swagger路径跳过认证")
        void swagger路径跳过认证() throws Exception {
            when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }
    }

    @Nested
    @DisplayName("JWT Token 解析测试")
    class JwtParsingTests {

        @Test
        @DisplayName("没有Authorization头直接放行")
        void 没有Authorization头直接放行() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }

        @Test
        @DisplayName("Authorization头不带Bearer前缀视为无token")
        void 不带Bearer前缀视为无token() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Basic abc123");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }

        @Test
        @DisplayName("Authorization头为空字符串视为无token")
        void Authorization头为空字符串视为无token() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }
    }

    @Nested
    @DisplayName("有效JWT Token测试")
    class ValidJwtTests {

        @Test
        @DisplayName("有效JWT设置SecurityContext认证")
        void 有效JWT设置认证() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtils.verifyToken("valid-token")).thenReturn(decodedJWT);
            when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
            when(decodedJWT.getClaim("userId")).thenReturn(userIdClaim);
            when(decodedJWT.getClaim("tenantId")).thenReturn(tenantIdClaim);
            when(emailClaim.asString()).thenReturn("test@example.com");
            when(userIdClaim.asLong()).thenReturn(1L);
            when(tenantIdClaim.asLong()).thenReturn(null);

            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setPassword("hashed");
            user.setUserLevel("FREE");
            when(userMapper.findByEmail("test@example.com")).thenReturn(user);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("SecurityContext已有认证跳过JWT处理")
        void 已有认证跳过JWT处理() throws Exception {
            // 先设置一个已有的认证
            SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("existing", null));

            when(request.getRequestURI()).thenReturn("/api/projects");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(jwtUtils);
        }
    }

    @Nested
    @DisplayName("JWT验证失败测试")
    class JwtFailureTests {

        @Test
        @DisplayName("JWT缺少用户信息返回401")
        void 缺少用户信息返回401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtils.verifyToken("valid-token")).thenReturn(decodedJWT);
            when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
            when(decodedJWT.getClaim("userId")).thenReturn(userIdClaim);
            when(emailClaim.asString()).thenReturn(null);
            when(userIdClaim.asLong()).thenReturn(1L);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertTrue(sw.toString().contains("Invalid token: missing user info"));
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("用户不存在于数据库返回401")
        void 用户不存在返回401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtils.verifyToken("valid-token")).thenReturn(decodedJWT);
            when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
            when(decodedJWT.getClaim("userId")).thenReturn(userIdClaim);
            when(emailClaim.asString()).thenReturn("test@example.com");
            when(userIdClaim.asLong()).thenReturn(1L);
            when(userMapper.findByEmail("test@example.com")).thenReturn(null);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertTrue(sw.toString().contains("Invalid token: user not found"));
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("TokenExpiredException返回401和Token expired消息")
        void token过期返回401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
            when(jwtUtils.verifyToken("expired-token")).thenThrow(new TokenExpiredException("Token expired", java.time.Instant.now()));

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertTrue(sw.toString().contains("Token expired"));
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("JWTVerificationException(签名错误)返回401和Invalid token消息")
        void 签名错误返回401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
            when(jwtUtils.verifyToken("bad-token")).thenThrow(new JWTVerificationException("Invalid signature"));

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertTrue(sw.toString().contains("Invalid token"));
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("用户ID不匹配返回401")
        void 用户ID不匹配返回401() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/projects");
            when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
            when(jwtUtils.verifyToken("valid-token")).thenReturn(decodedJWT);
            when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
            when(decodedJWT.getClaim("userId")).thenReturn(userIdClaim);
            when(emailClaim.asString()).thenReturn("test@example.com");
            when(userIdClaim.asLong()).thenReturn(1L);

            User user = new User();
            user.setId(999L); // ID不匹配
            user.setEmail("test@example.com");
            user.setPassword("hashed");
            user.setUserLevel("FREE");
            when(userMapper.findByEmail("test@example.com")).thenReturn(user);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            when(response.getWriter()).thenReturn(pw);

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            assertTrue(sw.toString().contains("Invalid token: user not found"));
            verify(chain, never()).doFilter(request, response);
        }
    }
}
