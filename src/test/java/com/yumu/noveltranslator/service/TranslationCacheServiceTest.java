package com.yumu.noveltranslator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.yumu.noveltranslator.entity.TranslationCache;
import com.yumu.noveltranslator.mapper.TranslationCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationCacheServiceTest {

    @Mock
    private TranslationCacheMapper translationCacheMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TranslationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TranslationCacheService(translationCacheMapper, stringRedisTemplate);
    }

    @Nested
    @DisplayName("L1 缓存")
    class L1CacheTests {

        @Test
        void getCacheHitsL1Caffeine() {
            // 先写入 L1
            cacheService.putToMemoryCache("test-key", "translated text");

            // L1 应命中
            String result = cacheService.getCache("test-key");
            assertEquals("translated text", result);
        }

        @Test
        void putToMemoryCacheStoresInL1() {
            cacheService.putToMemoryCache("key1", "value1");
            assertEquals("value1", cacheService.getCache("key1"));
        }

        @Test
        void getCacheReturnsNullWhenNoCacheExists() {
            // L1 miss, L2 miss (Redis returns null), L3 miss
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            String result = cacheService.getCache("test-key");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("L2 Redis 缓存")
    class L2RedisCacheTests {

        @Test
        void L2Redis命中回写L1() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("redis-value");

            String result = cacheService.getCache("test-key");

            assertEquals("redis-value", result);
            // Verify L2 was queried
            verify(valueOperations).get("translator:cache:test-key");
            // Verify value was written back to L1
            assertEquals("redis-value", cacheService.getCache("test-key"));
        }

        @Test
        void 空值占位返回null() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("__NULL__");

            String result = cacheService.getCache("test-key");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("L3 数据库缓存")
    class L3DatabaseCacheTests {

        @Test
        void L3数据库命中回写L2和L1() {
            // L1 miss, L2 miss, L3 hit
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById("test-key")).thenReturn(null);

            TranslationCache dbCache = new TranslationCache();
            dbCache.setCacheKey("test-key");
            dbCache.setTargetText("db-translated");
            dbCache.setExpireTime(LocalDateTime.now().plusHours(24));
            when(translationCacheMapper.selectOne(any())).thenReturn(dbCache);

            String result = cacheService.getCache("test-key");

            assertEquals("db-translated", result);
        }
    }

    @Nested
    @DisplayName("写入缓存")
    class PutCacheTests {

        @Test
        void putCache写入L1L2L3() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("cache-key", "source", "target", "en", "zh", "google");

            // Verify L1
            assertEquals("target", cacheService.getCache("cache-key"));
            // Verify L2 Redis write
            verify(valueOperations).set(eq("translator:cache:cache-key"), eq("target"), any(Duration.class));
        }

        @Test
        void putNullCache写入空值占位() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putNullCache("null-key");

            // L1 should have null placeholder
            assertNull(cacheService.getCache("null-key"));
            // Verify Redis was called with __NULL__
            verify(valueOperations).set(eq("translator:cache:null-key"), eq("__NULL__"), any());
        }
    }

    @Nested
    @DisplayName("其他方法")
    class OtherMethodsTests {

        @Test
        void hasCacheReturnsTrueWhenCached() {
            cacheService.putToMemoryCache("has-key", "value");
            assertTrue(cacheService.hasCache("has-key"));
        }

        @Test
        void hasCacheReturnsFalseWhenNotCached() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            assertFalse(cacheService.hasCache("nonexistent-key"));
        }

        @Test
        void cleanupExpiredCache调用删除() {
            when(translationCacheMapper.delete(any())).thenReturn(5);

            cacheService.cleanupExpiredCache();

            verify(translationCacheMapper).delete(any());
        }

        @Test
        void warmupCache加载热门缓存() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            TranslationCache dbCache = new TranslationCache();
            dbCache.setCacheKey("warm-key");
            dbCache.setTargetText("warm-value");
            dbCache.setExpireTime(LocalDateTime.now().plusHours(24));
            when(translationCacheMapper.selectById("warm-key")).thenReturn(dbCache);

            cacheService.warmupCache(java.util.List.of("warm-key"));

            assertEquals("warm-value", cacheService.getCache("warm-key"));
        }
    }
}
