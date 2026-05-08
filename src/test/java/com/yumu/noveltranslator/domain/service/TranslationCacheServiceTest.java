package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.adapter.out.redis.CacheVersionService;

import com.github.benmanes.caffeine.cache.Cache;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationCache;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationCacheServiceTest {

    @Mock
    private TranslationCacheMapper translationCacheMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CacheVersionService cacheVersionService;

    private TranslationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TranslationCacheService(translationCacheMapper, stringRedisTemplate, cacheVersionService);
        when(cacheVersionService.getVersion(anyString(), anyString())).thenReturn("1");
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
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

            // Verify L1 — key now has version prefix v1:
            assertEquals("target", cacheService.getCache("v1:cache-key"));
            // Verify L2 Redis write with versioned key
            verify(valueOperations).set(eq("translator:cache:v1:cache-key"), eq("target"), any(Duration.class));
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

    @Nested
    @DisplayName("分布式缓存加载锁")
    class DistributedCacheLockTests {

        @BeforeEach
        void setUpMocks() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        void 缓存命中L1不查询分布式锁() {
            cacheService.putToMemoryCache("lock-key", "l1-value");

            cacheService.getCache("lock-key");

            // Redis lock should not be called when L1 hits
            verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void L2查询时获取分布式锁() {
            // L1 miss, L2 Redis hit
            when(valueOperations.get(anyString())).thenReturn("redis-value");
            // Distributed lock acquired
            when(valueOperations.setIfAbsent(eq("cache:lock:test-key"), eq("1"), eq(30L), any())).thenReturn(true);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            String result = cacheService.getCache("test-key");

            assertEquals("redis-value", result);
            // Verify distributed lock was attempted
            verify(valueOperations).setIfAbsent(eq("cache:lock:test-key"), eq("1"), eq(30L), any());
        }

        @Test
        void 分布式锁获取失败返回null() {
            // L1 miss, L2 Redis miss, lock not acquired
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.setIfAbsent(eq("cache:lock:test-key"), eq("1"), eq(30L), any())).thenReturn(false);

            String result = cacheService.getCache("test-key");

            // Should fall through to L3, which should also miss
            // Since L3 loader also goes through loadWithLock, it will also fail to acquire lock
            // Result should be null (cache miss)
            assertNull(result);
        }

        @Test
        void 分布式锁获取成功后删除锁key() {
            // L1 miss, L2 Redis hit
            when(valueOperations.get(anyString())).thenReturn("locked-value");
            when(valueOperations.setIfAbsent(eq("cache:lock:test-key"), eq("1"), eq(30L), any())).thenReturn(true);
            when(stringRedisTemplate.delete(eq("cache:lock:test-key"))).thenReturn(true);

            cacheService.getCache("test-key");

            verify(stringRedisTemplate).delete("cache:lock:test-key");
        }

        @Test
        void 获取锁后重新检查L1缓存() {
            // First, put something in L1
            cacheService.putToMemoryCache("recheck-key", "l1-while-waiting");

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.setIfAbsent(eq("cache:lock:recheck-key"), eq("1"), eq(30L), any())).thenReturn(true);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            // Should return L1 value without calling loader (because another instance already loaded it)
            String result = cacheService.getCache("recheck-key");

            assertEquals("l1-while-waiting", result);
        }
    }
}
