package com.yumu.noveltranslator.adapter.in.security;
import com.yumu.noveltranslator.adapter.in.security.RedisSlidingWindowRateLimiter;
import com.yumu.noveltranslator.adapter.in.security.TranslationRateLimitFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationRateLimitFilter 单元测试")
class TranslationRateLimitFilterTest {

    @Mock
    private RedisSlidingWindowRateLimiter ipRateLimiter;

    @Mock
    private RedisSlidingWindowRateLimiter keyRateLimiter;

    @Mock
    private FilterChain chain;

    private TranslationRateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new TranslationRateLimitFilter(ipRateLimiter, keyRateLimiter, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("翻译路径下请求未超过限制时放行")
    void doFilter_translatePath_allowed() throws Exception {
        request.setRequestURI("/v1/translate/reader");
        request.setRemoteAddr("192.168.1.1");
        when(ipRateLimiter.allowRequest("192.168.1.1")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("翻译路径下请求超过限制时返回 429")
    void doFilter_translatePath_rateLimited() throws Exception {
        request.setRequestURI("/v1/translate/text/stream");
        request.setRemoteAddr("10.0.0.5");
        when(ipRateLimiter.allowRequest("10.0.0.5")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        String body = response.getContentAsString();
        assertTrue(body.contains("TOO_MANY_REQUESTS"));
        assertTrue(body.contains("IP rate limit exceeded"));
        assertTrue(body.contains("retryAfter"));
        assertEquals("60", response.getHeader("Retry-After"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("非翻译路径 shouldNotFilter 返回 true")
    void shouldNotFilter_nonTranslatePath_skips() {
        request.setRequestURI("/user/login");
        request.setRemoteAddr("192.168.1.1");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("翻译路径 shouldNotFilter 返回 false")
    void shouldNotFilter_translatePath_false() {
        request.setRequestURI("/v1/translate/reader");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("API Key 认证的请求仍经过 IP 限流")
    void doFilter_apiKeyRequest_skipsRateLimit() throws Exception {
        request.setRequestURI("/v1/translate/reader");
        request.setRemoteAddr("10.0.0.5");
        request.setAttribute("apiKeyId", 42L);
        when(ipRateLimiter.allowRequest("10.0.0.5")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(ipRateLimiter).allowRequest("10.0.0.5");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("X-Forwarded-For 头正确提取客户端 IP")
    void doFilter_xForwardedFor_extractsFirstIp() throws Exception {
        request.setRequestURI("/v1/translate/selection");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.17");
        when(ipRateLimiter.allowRequest("203.0.113.50")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(ipRateLimiter).allowRequest("203.0.113.50");
    }

    @Test
    @DisplayName("X-Real-IP 头作为回退提取客户端 IP")
    void doFilter_xRealIp_fallback() throws Exception {
        request.setRequestURI("/v1/translate/reader");
        request.addHeader("X-Real-IP", "198.51.100.77");
        when(ipRateLimiter.allowRequest("198.51.100.77")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(ipRateLimiter).allowRequest("198.51.100.77");
    }
}
