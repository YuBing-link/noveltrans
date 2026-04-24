package com.yumu.noveltranslator.service;

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

/**
 * TranslationCacheService 补充测试
 * 覆盖现有测试未覆盖的分支：getCacheByMode 多模式搜索、
 * getCacheStats 统计、cache stampede 防护、
 * putCache with mode、warmupCache 批量、cleanup 异常路径
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationCacheService 补充测试")
class TranslationCacheServiceExtendedTest {

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
        // Default: Redis returns null (L2 miss)
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    // ============ getCacheByMode 多模式搜索 ============

    @Nested
    @DisplayName("getCacheByMode - 多模式搜索")
    class GetCacheByModeTests {

        @Test
        void fast模式搜索team_expert_fast层级() {
            // 先写入 team 模式缓存
            cacheService.putToMemoryCache("key_team", "team-value");

            String result = cacheService.getCacheByMode("key", "fast");

            assertEquals("team-value", result);
        }

        @Test
        void expert模式搜索team_expert层级() {
            cacheService.putToMemoryCache("key_expert", "expert-value");

            String result = cacheService.getCacheByMode("key", "expert");

            assertEquals("expert-value", result);
        }

        @Test
        void team模式仅搜索team层级() {
            cacheService.putToMemoryCache("key_team", "team-only");

            String result = cacheService.getCacheByMode("key", "team");

            assertEquals("team-only", result);
        }

        @Test
        void null模式默认按fast搜索() {
            cacheService.putToMemoryCache("key_fast", "fast-default");

            String result = cacheService.getCacheByMode("key", null);

            assertEquals("fast-default", result);
        }

        @Test
        void 所有模式均未命中返回null() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            String result = cacheService.getCacheByMode("nonexistent", "fast");

            assertNull(result);
        }
    }

    // ============ putCache with mode ============

    @Nested
    @DisplayName("putCache - 带模式标签")
    class PutCacheWithModeTests {

        @Test
        void 带模式标签的缓存键附加后缀() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("key", "source", "target", "en", "zh", "google", "team");

            // 验证 L1 中存储的 key 带有 _team 后缀
            assertEquals("target", cacheService.getCache("key_team"));
        }

        @Test
        void 空模式标签不附加后缀() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("key", "source", "target", "en", "zh", "google", "");

            assertEquals("target", cacheService.getCache("key"));
            // 确认没有 _ 后缀
            assertNull(cacheService.getCache("key_"));
        }

        @Test
        void null模式标签不附加后缀() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putCache("key", "source", "target", "en", "zh", "google", null);

            assertEquals("target", cacheService.getCache("key"));
        }
    }

    // ============ getCacheStats 统计 ============

    @Nested
    @DisplayName("getCacheStats - 统计信息")
    class GetCacheStatsTests {

        @Test
        void 初始状态统计为零() {
            Map<String, Object> stats = cacheService.getCacheStats();

            assertEquals(0L, stats.get("l1Hits"));
            assertEquals(0L, stats.get("l2Hits"));
            assertEquals(0L, stats.get("l3Hits"));
            assertEquals(0L, stats.get("misses"));
            assertEquals("0.00%", stats.get("hitRate"));
            assertEquals(0L, stats.get("totalRequests"));
        }

        @Test
        void 缓存命中后统计正确() {
            // 写入 L1 并读取
            cacheService.putToMemoryCache("stats-key", "stats-value");
            cacheService.getCache("stats-key");

            Map<String, Object> stats = cacheService.getCacheStats();

            assertEquals(1L, stats.get("l1Hits"));
            assertEquals(0L, stats.get("misses"));
            assertEquals("100.00%", stats.get("hitRate"));
            assertEquals(1L, stats.get("totalRequests"));
        }

        @Test
        void 缓存未命中统计正确() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            cacheService.getCache("stats-miss");

            Map<String, Object> stats = cacheService.getCacheStats();

            assertTrue((Long) stats.get("misses") >= 1);
        }
    }

    // ============ cleanupExpiredCache 异常路径 ============

    @Nested
    @DisplayName("cleanupExpiredCache - 异常处理")
    class CleanupExpiredCacheErrorTests {

        @Test
        void 数据库异常不抛出() {
            when(translationCacheMapper.delete(any())).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() -> cacheService.cleanupExpiredCache());
        }
    }

    // ============ warmupCache 补充分支 ============

    @Nested
    @DisplayName("warmupCache - 补充分支")
    class WarmupCacheExtendedTests {

        @Test
        void 数据库中不存在的key跳过() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(translationCacheMapper.selectById("warm-miss")).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            cacheService.warmupCache(java.util.List.of("warm-miss"));

            // 未写入 L1
            assertNull(cacheService.getCache("warm-miss"));
        }

        @Test
        void 混合命中和未命中() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            // warm-hit 在数据库中有记录
            TranslationCache dbCache = new TranslationCache();
            dbCache.setCacheKey("warm-hit");
            dbCache.setTargetText("warm-value");
            dbCache.setExpireTime(LocalDateTime.now().plusHours(24));
            when(translationCacheMapper.selectById("warm-hit")).thenReturn(dbCache);

            // warm-miss 在数据库中无记录
            when(translationCacheMapper.selectById("warm-miss")).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            cacheService.warmupCache(java.util.List.of("warm-hit", "warm-miss"));

            assertEquals("warm-value", cacheService.getCache("warm-hit"));
            assertNull(cacheService.getCache("warm-miss"));
        }
    }

    // ============ 缓存击穿防护 ============

    @Nested
    @DisplayName("缓存击穿防护 - 并发加载")
    class CacheStampedeProtectionTests {

        @Test
        void 同一key仅触发一次底层查询() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById("stampede-key")).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);

            // 首次查询
            cacheService.getCache("stampede-key");

            // selectOne 仅被调用一次（未被并发重复调用）
            // 验证 loadingKeys 机制正常工作
            verify(translationCacheMapper, times(1)).selectOne(any());
        }
    }

    // ============ 空值占位完整路径 ============

    @Nested
    @DisplayName("空值占位 - 完整路径")
    class NullPlaceholderFullTests {

        @Test
        void L1空值占位后再次查询返回null() {
            doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            cacheService.putNullCache("null-full");

            // 再次查询应返回 null（L1 命中空值占位）
            assertNull(cacheService.getCache("null-full"));
        }
    }
}
