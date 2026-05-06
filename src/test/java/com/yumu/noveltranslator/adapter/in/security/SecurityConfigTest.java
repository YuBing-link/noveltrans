package com.yumu.noveltranslator.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SecurityConfig CORS 配置测试")
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Mock
    private com.yumu.noveltranslator.config.tenant.TenantCleanupInterceptor tenantCleanupInterceptor;

    @Mock
    private TranslationIpRateLimiter translationIpRateLimiter;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private com.yumu.noveltranslator.util.JwtUtils jwtUtils;

    @Mock
    private com.yumu.noveltranslator.mapper.UserMapper userMapper;

    @Mock
    private com.yumu.noveltranslator.service.TokenBlacklistService tokenBlacklistService;

    @Mock
    private com.yumu.noveltranslator.mapper.ApiKeyMapper apiKeyMapper;

    private SecurityConfig createConfig() {
        return new SecurityConfig(
                jwtAuthenticationEntryPoint, tenantCleanupInterceptor,
                translationIpRateLimiter, objectMapper,
                jwtUtils, userMapper, tokenBlacklistService, apiKeyMapper);
    }

    @Nested
    @DisplayName("CORS 配置 - 默认环境")
    class CorsDefaultConfigTests {

        @Test
        void 本地开发端口允许() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn("http://localhost:7341");

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNotNull(cors);
            assertTrue(cors.getAllowedOriginPatterns().contains("http://localhost:7341"));
            assertTrue(cors.getAllowCredentials());
        }

        @Test
        void 扩展允许来源() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn(
                    "chrome-extension://imhobepmmpncjlobbicamollfjldiodi");

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNotNull(cors);
            assertTrue(cors.getAllowedOriginPatterns().contains(
                    "chrome-extension://imhobepmmpncjlobbicamollfjldiodi"));
        }

        @Test
        void 非白名单来源拒绝() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn("http://evil.com");

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNull(cors);
        }

        @Test
        void 无Origin头拒绝() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn(null);

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNull(cors);
        }

        @Test
        void 允许所有HTTP方法() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn("http://localhost:7341");

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNotNull(cors);
            assertTrue(cors.getAllowedMethods().contains("GET"));
            assertTrue(cors.getAllowedMethods().contains("POST"));
            assertTrue(cors.getAllowedMethods().contains("PUT"));
            assertTrue(cors.getAllowedMethods().contains("DELETE"));
            assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
        }
    }

    @Nested
    @DisplayName("CORS 配置 - 环境变量覆盖")
    class CorsEnvOverrideTests {

        @Test
        void CORS_ALLOWED_ORIGINS环境变量覆盖默认值() {
            SecurityConfig config = createConfig();
            CorsConfigurationSource source = config.corsConfigurationSource();

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Origin")).thenReturn("http://localhost:7341");

            CorsConfiguration cors = source.getCorsConfiguration(request);

            assertNotNull(cors);
        }
    }
}
