package com.yumu.noveltranslator.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based sliding window rate limiter for translation endpoints at IP level.
 * Prevents attackers with stolen tokens from bypassing per-user limits
 * by creating multiple accounts from the same IP.
 */
@Component
@Slf4j
public class TranslationIpRateLimiter {

    private static final String KEY_PREFIX = "translation:ip_limit:";

    private final StringRedisTemplate redisTemplate;
    private final long windowSeconds;
    private final int maxRequests;

    public TranslationIpRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${translation.ip-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${translation.ip-rate-limit.max-requests:100}") int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    /**
     * Check if a request from the given IP should be allowed.
     * Uses Redis Sorted Set sliding window: removes entries older than the window,
     * adds the current timestamp, and checks the count.
     *
     * @param clientIp the client IP address
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String clientIp) {
        String key = KEY_PREFIX + clientIp;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        try {
            // Remove entries older than the window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Add current timestamp with score = now
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);

            // Set expiry on the key to auto-clean up
            redisTemplate.expire(key, windowSeconds + 10, TimeUnit.SECONDS);

            // Count entries within the window
            Long count = redisTemplate.opsForZSet().zCard(key);
            if (count != null && count > maxRequests) {
                log.warn("IP {} 翻译请求超过限制 ({} 次/{}s), 当前计数: {}",
                        clientIp, maxRequests, windowSeconds, count);
                return false;
            }
            return true;
        } catch (Exception e) {
            // On Redis failure, allow the request to avoid DoS
            log.error("Redis rate limiter error for IP {}: {}", clientIp, e.getMessage(), e);
            return true;
        }
    }
}
