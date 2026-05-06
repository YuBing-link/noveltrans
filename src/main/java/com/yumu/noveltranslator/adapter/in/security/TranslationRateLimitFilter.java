package com.yumu.noveltranslator.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.yumu.noveltranslator.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import com.yumu.noveltranslator.util.SecurityUtil;

/**
 * IP-level rate limiting filter for translation endpoints.
 * Runs before JWT authentication to block abusive IPs regardless of which
 * account (stolen or otherwise) they use.
 * <p>
 * For API Key authenticated requests (detected via Authorization header prefix),
 * applies per-key rate limiting instead of IP-based limiting.
 * <p>
 * NOTE: Not a @Component — declared as @Bean in SecurityConfig to avoid
 * CGLIB proxy issues with Spring Security's filter registration.
 */
@Slf4j
public class TranslationRateLimitFilter extends OncePerRequestFilter {

    private static final int RETRY_AFTER_SECONDS = 60;
    private static final String API_KEY_PREFIX = "nt_sk_";

    private final RedisSlidingWindowRateLimiter ipRateLimiter;
    private final RedisSlidingWindowRateLimiter keyRateLimiter;
    private final ObjectMapper objectMapper;

    public TranslationRateLimitFilter(RedisSlidingWindowRateLimiter ipRateLimiter,
                                       RedisSlidingWindowRateLimiter keyRateLimiter,
                                       ObjectMapper objectMapper) {
        this.ipRateLimiter = ipRateLimiter;
        this.keyRateLimiter = keyRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/translate/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String jwt = parseJwt(request);
        boolean isApiKeyRequest = jwt != null && jwt.startsWith(API_KEY_PREFIX);

        boolean allowed;
        if (isApiKeyRequest) {
            allowed = keyRateLimiter.allowRequest(jwt);
        } else {
            String clientIp = SecurityUtil.getClientIp(request);
            allowed = ipRateLimiter.allowRequest(clientIp);
        }

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "TOO_MANY_REQUESTS");
            body.put("message", isApiKeyRequest ? "API Key rate limit exceeded" : "IP rate limit exceeded");
            body.put("retryAfter", RETRY_AFTER_SECONDS);

            response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
            objectMapper.writeValue(response.getWriter(), body);
            response.flushBuffer();
            return;
        }

        chain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
