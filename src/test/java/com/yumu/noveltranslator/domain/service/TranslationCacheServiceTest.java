package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import com.yumu.noveltranslator.adapter.out.redis.CacheVersionService;

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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.test.util.ReflectionTestUtils;

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
    private SetOperations<String, String> setOperations;

    @Mock
    private CacheVersionService cacheVersionService;

    private TranslationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TranslationCacheService(translationCacheMapper, stringRedisTemplate, cacheVersionService);
        when(cacheVersionService.getVersion(anyString(), anyString())).thenReturn("1");
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);
        lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("L1 缓存")
    class L1CacheTests {

        @Test
        void getCacheHitsL1Caffeine() {
            // putToMemoryCache 使用裸 key，getCache 内部加 v1: 前缀查 versionedKey
            // 所以这里需要写入 versioned key
            cacheService.putToMemoryCache("v1:test-key", "translated text");

            String result = cacheService.getCache("test-key");
            assertEquals("translated text", result);
        }

        @Test
        void putToMemoryCacheStoresInL1() {
            cacheService.putToMemoryCache("v1:key1", "value1");
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
            // Verify L2 was queried with versioned key
            verify(valueOperations).get("translator:cache:v1:test-key");
            // Verify value was written back to L1 (versioned key)
            assertEquals("redis-value", cacheService.getCache("test-key"));
        }

    }

    @Nested
    @DisplayName("L3 数据库缓存")
    class L3DatabaseCacheTests {

        @Test
        void L1和L2都未命中返回null() {
            // L1 miss, L2 miss — getCache 不再查询 L3
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            String result = cacheService.getCache("test-key");

            assertNull(result);
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
    }

    @Nested
    @DisplayName("其他方法")
    class OtherMethodsTests {

        @Test
        void hasCacheReturnsTrueWhenCached() {
            cacheService.putToMemoryCache("v1:has-key", "value");
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
            dbCache.setCacheKey("v1:warm-key");
            dbCache.setTargetText("warm-value");
            dbCache.setExpireTime(LocalDateTime.now().plusHours(24));
            when(translationCacheMapper.selectById("v1:warm-key")).thenReturn(dbCache);

            cacheService.warmupCache(java.util.List.of("v1:warm-key"));

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
            cacheService.putToMemoryCache("v1:lock-key", "l1-value");

            cacheService.getCache("lock-key");

            // Redis lock should not be called when L1 hits
            verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void L2查询直接返回无锁操作() {
            // L1 miss, L2 Redis hit
            when(valueOperations.get(anyString())).thenReturn("redis-value");

            String result = cacheService.getCache("test-key");

            assertEquals("redis-value", result);
            verify(valueOperations).get("translator:cache:v1:test-key");
        }

        @Test
        void L1和L2都未命中返回null() {
            // L1 miss, L2 Redis miss
            lenient().when(valueOperations.get(anyString())).thenReturn(null);
            // tryAcquireSimpleLock calls setIfAbsent with Duration (3 params)
            lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            String result = cacheService.getCache("test-key");

            assertNull(result);
        }

        @Test
        void 获取锁后重新检查L1缓存() {
            // First, put something in L1 with versioned key
            cacheService.putToMemoryCache("v1:recheck-key", "l1-while-waiting");

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.setIfAbsent(eq("translator:lock:v1:recheck-key"), eq("1"), eq(60L), any())).thenReturn(true);
            when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            // Should return L1 value without calling loader (because another instance already loaded it)
            String result = cacheService.getCache("recheck-key");

            assertEquals("l1-while-waiting", result);
        }
    }

    @Nested
    @DisplayName("模式缓存层级查询")
    class ModeCacheHierarchyTests {

        @Test
        void getCacheByMode_L1命中返回() {
            // Write to L1 with versioned mode suffix
            cacheService.putToMemoryCache("v1:mode-key_team", "team-translated");

            String result = cacheService.getCacheByMode("mode-key", "fast");

            assertEquals("team-translated", result);
        }
    }

    @Nested
    @DisplayName("术语反向索引")
    class TermReverseIndexTests {

        @Test
        void buildReverseIndex提取单词() {
            when(stringRedisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
            lenient().when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);

            ReflectionTestUtils.invokeMethod(cacheService, "buildReverseIndex", "cache-1", "The quick brown fox jumps over the lazy dog");

            verify(stringRedisTemplate.opsForSet(), atLeastOnce()).add(anyString(), eq("cache-1"));
        }

        @Test
        void buildReverseIndex空文本跳过() {
            ReflectionTestUtils.invokeMethod(cacheService, "buildReverseIndex", "cache-1", "");
            ReflectionTestUtils.invokeMethod(cacheService, "buildReverseIndex", "cache-1", null);

            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        void buildReverseIndex去重单词() {
            when(stringRedisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
            lenient().when(stringRedisTemplate.expire(anyString(), any())).thenReturn(true);

            // Same word repeated
            ReflectionTestUtils.invokeMethod(cacheService, "buildReverseIndex", "cache-1", "hello hello hello");

            // Should only add once (unique words)
            verify(stringRedisTemplate.opsForSet(), times(1)).add(anyString(), eq("cache-1"));
        }

        @Test
        void invalidateKeysForTerm失效匹配键() {
            when(stringRedisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
            Set<String> cacheKeys = Set.of("key1", "key2");
            when(stringRedisTemplate.opsForSet().members("glossary:cache_keys:apple")).thenReturn(cacheKeys);
            lenient().when(stringRedisTemplate.delete(anyCollection())).thenReturn(2L);
            lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
            lenient().when(translationCacheMapper.delete(any())).thenReturn(1);

            cacheService.invalidateKeysForTerm("Apple");

            // L1 should be invalidated (key is bare, so invalidate works on whatever was stored)
            assertNull(cacheService.getCache("key1"));
        }

        @Test
        void invalidateKeysForTerm_空词跳过() {
            cacheService.invalidateKeysForTerm(null);
            cacheService.invalidateKeysForTerm("");
            cacheService.invalidateKeysForTerm("   ");

            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        void invalidateKeysForTerm_无匹配键直接返回() {
            when(stringRedisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
            when(stringRedisTemplate.opsForSet().members(anyString())).thenReturn(Set.of());

            cacheService.invalidateKeysForTerm("nonexistent");

            // Should not attempt deletion
            verify(stringRedisTemplate, never()).delete(anyCollection());
        }

        @Test
        void invalidateKeysForTerm_Redis删除失败继续() {
            when(stringRedisTemplate.opsForSet()).thenReturn(mock(org.springframework.data.redis.core.SetOperations.class));
            Set<String> cacheKeys = Set.of("key1");
            when(stringRedisTemplate.opsForSet().members("glossary:cache_keys:word")).thenReturn(cacheKeys);
            lenient().when(stringRedisTemplate.delete(anyCollection())).thenThrow(new RuntimeException("redis error"));
            lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);

            // Should not throw — handles Redis failure gracefully
            assertDoesNotThrow(() -> cacheService.invalidateKeysForTerm("word"));
        }
    }

    @Nested
    @DisplayName("缓存统计和管理")
    class CacheStatsTests {

        @Test
        void getCacheStats_初始值为0() {
            Map<String, Object> stats = cacheService.getCacheStats();

            assertEquals(0L, stats.get("l1Hits"));
            assertEquals(0L, stats.get("l2Hits"));
            assertEquals(0L, stats.get("misses"));
            assertEquals("0.00%", stats.get("hitRate"));
            assertEquals(0L, stats.get("totalRequests"));
        }

        @Test
        void getCacheStats_记录命中() {
            cacheService.putToMemoryCache("v1:stat-key", "stat-value");
            cacheService.getCache("stat-key"); // L1 hit
            cacheService.getCache("miss-key"); // miss

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            Map<String, Object> stats = cacheService.getCacheStats();

            assertTrue((Long) stats.get("l1Hits") >= 1);
            assertTrue((Long) stats.get("misses") >= 1);
        }

        @Test
        void clearLocalCache清空Caffeine() {
            cacheService.putToMemoryCache("v1:a", "1");
            cacheService.putToMemoryCache("v1:b", "2");

            cacheService.clearLocalCache();

            assertNull(cacheService.getCache("a"));
            assertNull(cacheService.getCache("b"));
        }

        @Test
        void clearAllCache_清空所有层() {
            cacheService.putToMemoryCache("v1:clear-key", "clear-value");
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(stringRedisTemplate.keys(REDIS_KEY_PREFIX + "*")).thenReturn(Set.of());
            lenient().when(stringRedisTemplate.delete(anyCollection())).thenReturn(0L);
            lenient().when(translationCacheMapper.delete(any())).thenReturn(0);

            cacheService.clearAllCache();

            assertNull(cacheService.getCache("clear-key"));
        }

        private static final String REDIS_KEY_PREFIX = "translator:cache:";
    }

    @Nested
    @DisplayName("带模式的缓存写入")
    class PutCacheWithModeTests {

        @Test
        void putCache_带模式写入L2() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("mode-key", "source", "target", "en", "zh", "google", "fast");

            // Verify key has mode suffix and version prefix
            verify(valueOperations).set(eq("translator:cache:v1:mode-key_fast"), eq("target"), any());
        }

        @Test
        void putCache_null模式不附加后缀() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("simple-key", "source", "target", "en", "zh", "google", null);

            verify(valueOperations).set(eq("translator:cache:v1:simple-key"), eq("target"), any());
        }
    }
}
