package com.yumu.noveltranslator.adapter.in.security;
import com.yumu.noveltranslator.adapter.in.security.RedisSlidingWindowRateLimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisSlidingWindowRateLimiter 单元测试")
class RedisSlidingWindowRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisSlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisSlidingWindowRateLimiter(redisTemplate, "test:prefix", 60, 5);
    }

    @Test
    @DisplayName("请求在限制范围内时允许通过")
    void allowRequest_withinLimit() {
        String ip = "192.168.1.1";
        doReturn(3L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertTrue(limiter.allowRequest(ip));

        verify(redisTemplate).execute(any(RedisScript.class), eq(java.util.List.of("test:prefix192.168.1.1")), any(Object[].class));
    }

    @Test
    @DisplayName("超过限制时拒绝请求")
    void allowRequest_exceedsLimit_blocks() {
        String ip = "10.0.0.1";
        // 当前计数超过 maxRequests(5)
        doReturn(6L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertFalse(limiter.allowRequest(ip));
    }

    @Test
    @DisplayName("Redis 异常时允许请求以避免 DoS")
    void allowRequest_redisFailure_allows() {
        String ip = "10.0.0.2";
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        // 即使 Redis 出错，也应该允许请求以避免 DoS
        assertTrue(limiter.allowRequest(ip));
    }

    @Test
    @DisplayName("execute 返回 null 时允许请求")
    void allowRequest_nullCount_allows() {
        String ip = "172.16.0.1";
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertTrue(limiter.allowRequest(ip));
    }
}
