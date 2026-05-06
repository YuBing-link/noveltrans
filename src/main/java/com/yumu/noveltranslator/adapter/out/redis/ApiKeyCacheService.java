package com.yumu.noveltranslator.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * API Key 认证缓存服务。
 * 两级缓存：Caffeine L1（5 分钟） → Redis L2（30 分钟） → MySQL（由 Filter 兜底）。
 * 在 ApiKeyAuthenticationFilter 中使用，避免热路径同步查 MySQL。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyCacheService {

    /** Redis key 前缀: apikey:{token} */
    private static final String REDIS_KEY_PREFIX = "apikey:";
    private static final long REDIS_TTL_MINUTES = 30;
    private static final long CAFFEINE_TTL_MINUTES = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Caffeine L1 本地缓存 */
    private final Cache<String, ApiKeyAuthInfo> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(CAFFEINE_TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * 获取 API Key 认证信息（两级缓存 + null 标记）
     * @return ApiKeyAuthInfo，缓存中不存在或已禁用时返回 null
     */
    public ApiKeyAuthInfo get(String token) {
        // L1: Caffeine
        ApiKeyAuthInfo cached = localCache.getIfPresent(token);
        if (cached != null) {
            return cached.isDisabled() ? null : cached;
        }

        // L2: Redis
        String redisKey = REDIS_KEY_PREFIX + token;
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (json != null) {
            try {
                ApiKeyAuthInfo info = objectMapper.readValue(json, ApiKeyAuthInfo.class);
                if (info.isDisabled()) {
                    return null;
                }
                // 写回 L1
                localCache.put(token, info);
                return info;
            } catch (Exception e) {
                log.warn("Redis API Key 缓存反序列化失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 将 API Key 信息写入缓存（L1 + L2）
     */
    public void put(String token, ApiKeyAuthInfo info) {
        // L1
        localCache.put(token, info);

        // L2
        if (info.isDisabled()) {
            return;
        }
        String redisKey = REDIS_KEY_PREFIX + token;
        try {
            String json = objectMapper.writeValueAsString(info);
            stringRedisTemplate.opsForValue().set(redisKey, json, REDIS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis API Key 缓存写入失败: token={}, error={}", maskToken(token), e.getMessage());
        }
    }

    /**
     * 标记 API Key 为禁用（清除缓存）
     */
    public void invalidate(String token) {
        localCache.invalidate(token);
        String redisKey = REDIS_KEY_PREFIX + token;
        stringRedisTemplate.delete(redisKey);
        // 发布 pub/sub 事件通知其他实例清 L1（复用 ADR-002 模式）
        stringRedisTemplate.convertAndSend("apikey:invalidation", token);
    }

    /**
     * 记录 API Key 使用次数（Redis INCR，异步回写 MySQL）
     */
    public void incrementUsage(Long apiKeyId) {
        String key = "apikey:usage:" + apiKeyId;
        // 仅 INCR，EXPIRE 由 flush 后的首次写入设置，避免每次请求额外一次 Redis 调用
        stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 批量刷新使用次数到 MySQL
     * @return 本次刷新的 key 数量
     */
    public int flushUsage(ApiKeyFlushCallback callback) {
        String pattern = "apikey:usage:*";
        int flushed = 0;

        try {
            java.util.Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            for (String key : keys) {
                String usageStr = stringRedisTemplate.opsForValue().get(key);
                if (usageStr != null) {
                    try {
                        long usage = Long.parseLong(usageStr);
                        String idStr = key.substring("apikey:usage:".length());
                        Long apiKeyId = Long.parseLong(idStr);
                        callback.flush(apiKeyId, usage);
                        stringRedisTemplate.delete(key);
                        flushed++;
                    } catch (NumberFormatException e) {
                        log.warn("API Key usage flush 解析失败: key={}, value={}", key, usageStr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("API Key usage flush 异常: {}", e.getMessage());
        }

        if (flushed > 0) {
            log.info("API Key usage flush: 刷新了 {} 个 key", flushed);
        }
        return flushed;
    }

    /**
     * 强制清空 L1 缓存（用于 pub/sub 事件处理）
     */
    public void invalidateAllLocal() {
        localCache.invalidateAll();
        log.info("ApiKeyCacheService 清空 L1 本地缓存");
    }

    public Cache<String, ApiKeyAuthInfo> getLocalCache() {
        return localCache;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 16) return "***";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 4);
    }

    /**
     * MySQL flush 回调接口，由 ApiKeyUsageFlushTask 提供实现
     */
    public interface ApiKeyFlushCallback {
        void flush(Long apiKeyId, long usage);
    }

    /**
     * 缓存值对象
     */
    @Data
    public static class ApiKeyAuthInfo {
        private Long apiKeyId;
        private Long userId;
        private String userLevel;
        private Long tenantId;
        private boolean disabled;

        public static ApiKeyAuthInfo disabled() {
            ApiKeyAuthInfo info = new ApiKeyAuthInfo();
            info.setDisabled(true);
            return info;
        }
    }
}
