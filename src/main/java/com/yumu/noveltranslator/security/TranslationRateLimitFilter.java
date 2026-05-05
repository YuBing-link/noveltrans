package com.yumu.noveltranslator.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IP-level rate limiting filter for translation endpoints.
 * Runs before JWT authentication to block abusive IPs regardless of which
 * account (stolen or otherwise) they use.
 * <p>
 * Skips rate limiting for authenticated API Key requests, which already
 * have per-key tracking at the application layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationRateLimitFilter extends OncePerRequestFilter {

    private static final int RETRY_AFTER_SECONDS = 60;

    private final TranslationIpRateLimiter translationIpRateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/translate/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // Skip rate limiting for API Key authenticated requests
        if (request.getAttribute("apiKeyId") != null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);

        if (!translationIpRateLimiter.allowRequest(clientIp)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "TOO_MANY_REQUESTS");
            body.put("message", "IP rate limit exceeded");
            body.put("retryAfter", RETRY_AFTER_SECONDS);

            response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
            objectMapper.writeValue(response.getWriter(), body);
            response.flushBuffer();
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Extract the real client IP, checking proxy headers first.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
            return xff.split(",")[0].trim();
        }

        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) {
            return xri.trim();
        }

        return request.getRemoteAddr();
    }
}
