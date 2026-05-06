package com.yumu.noveltranslator.adapter.in.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Generic Redis sliding window rate limiter.
 * Uses a single Lua script (4 operations -> 1 atomic call).
 */
@Slf4j
public class RedisSlidingWindowRateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowStart = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local maxRequests = tonumber(ARGV[4])

            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            redis.call('ZADD', key, now, now)
            redis.call('EXPIRE', key, ttl)
            local count = redis.call('ZCARD', key)
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final long windowSeconds;
    private final int maxRequests;
    private final DefaultRedisScript<Long> script;

    public RedisSlidingWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            long windowSeconds,
            int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
        this.script = new DefaultRedisScript<>(SCRIPT, Long.class);
    }

    public boolean allowRequest(String identifier) {
        String key = keyPrefix + identifier;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        try {
            Long count = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowStart),
                    String.valueOf(windowSeconds + 10),
                    String.valueOf(maxRequests));

            if (count != null && count > maxRequests) {
                log.warn("Rate limit exceeded for {} (key: {}) - {} requests in {}s window",
                        identifier, keyPrefix, count, windowSeconds);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Redis rate limiter error for {} (key: {}): {}", identifier, keyPrefix, e.getMessage(), e);
            return true;
        }
    }
}
