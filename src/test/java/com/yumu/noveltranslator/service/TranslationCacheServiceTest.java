package com.yumu.noveltranslator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.yumu.noveltranslator.entity.TranslationCache;
import com.yumu.noveltranslator.mapper.TranslationCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    void getCacheStatsReturnsValidMap() {
        Map<String, Object> stats = cacheService.getCacheStats();
        assertTrue(stats.containsKey("l1Hits"));
        assertTrue(stats.containsKey("l2Hits"));
        assertTrue(stats.containsKey("l3Hits"));
        assertTrue(stats.containsKey("misses"));
        assertTrue(stats.containsKey("hitRate"));
        assertTrue(stats.containsKey("totalRequests"));
    }

    @Test
    void getCacheStatsInitiallyZeroHits() {
        Map<String, Object> stats = cacheService.getCacheStats();
        assertEquals(0L, stats.get("l1Hits"));
        assertEquals(0L, stats.get("l2Hits"));
        assertEquals(0L, stats.get("l3Hits"));
        assertEquals(0L, stats.get("misses"));
    }
}
