package com.yumu.noveltranslator.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JWT 认证缓存服务。
 * 两级缓存：Caffeine L1（5 分钟） → Redis L2（30 分钟） → MySQL 兜底。
 * 在 JwtAuthenticationFilter 中使用，避免热路径同步查 MySQL。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JwtAuthCacheService {

    /** Redis key 前缀: jwt:auth:{userId} */
    private static final String REDIS_KEY_PREFIX = "jwt:auth:";
    private static final long REDIS_TTL_MINUTES = 30;
    private static final long CAFFEINE_TTL_MINUTES = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Caffeine L1 本地缓存 */
    private final Cache<Long, JwtAuthInfo> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(CAFFEINE_TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 获取用户认证信息（两级缓存）
     * @return JwtAuthInfo，缓存中不存在时返回 null
     */
    public JwtAuthInfo get(Long userId) {
        // L1: Caffeine
        JwtAuthInfo cached = localCache.getIfPresent(userId);
        if (cached != null) {
            return cached.isDisabled() ? null : cached;
        }

        // L2: Redis
        String redisKey = REDIS_KEY_PREFIX + userId;
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (json != null) {
            try {
                JwtAuthInfo info = objectMapper.readValue(json, JwtAuthInfo.class);
                if (info.isDisabled()) {
                    return null;
                }
                // 写回 L1
                localCache.put(userId, info);
                return info;
            } catch (Exception e) {
                log.warn("Redis JWT 缓存反序列化失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 将用户认证信息写入缓存（L1 + L2）
     */
    public void put(Long userId, JwtAuthInfo info) {
        // L1
        localCache.put(userId, info);

        // L2
        if (info.isDisabled()) {
            return;
        }
        String redisKey = REDIS_KEY_PREFIX + userId;
        try {
            String json = objectMapper.writeValueAsString(info);
            stringRedisTemplate.opsForValue().set(redisKey, json, REDIS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis JWT 缓存写入失败: userId={}", userId);
        }
    }

    /**
     * 标记用户为禁用（清除缓存）
     */
    public void invalidate(Long userId) {
        localCache.invalidate(userId);
        String redisKey = REDIS_KEY_PREFIX + userId;
        stringRedisTemplate.delete(redisKey);
        // 发布 pub/sub 事件通知其他实例清 L1
        stringRedisTemplate.convertAndSend("jwt:invalidation", String.valueOf(userId));
    }

    /**
     * 强制清空 L1 缓存（用于 pub/sub 事件处理）
     */
    public void invalidateAllLocal() {
        localCache.invalidateAll();
        log.info("JwtAuthCacheService 清空 L1 本地缓存");
    }

    public Cache<Long, JwtAuthInfo> getLocalCache() {
        return localCache;
    }

    /**
     * 缓存值对象
     */
    @Data
    public static class JwtAuthInfo {
        private Long userId;
        private String email;
        private String userLevel;
        private Long tenantId;
        private boolean disabled;

        public static JwtAuthInfo disabled() {
            JwtAuthInfo info = new JwtAuthInfo();
            info.setDisabled(true);
            return info;
        }
    }
}
