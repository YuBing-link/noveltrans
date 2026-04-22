package com.yumu.noveltranslator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentEntityCache 测试")
class DocumentEntityCacheTest {

    private DocumentEntityCache cache;

    @BeforeEach
    void setUp() {
        cache = new DocumentEntityCache();
    }

    @Test
    void getEntityMapReturnsEmptyForUnknownKey() {
        assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
    }

    @Test
    void mergeAndGetEntityMap() {
        cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
        Map<String, String> map = cache.getEntityMap(1L, "doc-001");
        assertEquals("你好", map.get("Hello"));
    }

    @Test
    void mergeMergesWithExistingData() {
        cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
        cache.mergeEntityMap(1L, "doc-001", Map.of("World", "世界"));
        Map<String, String> map = cache.getEntityMap(1L, "doc-001");
        assertEquals(2, map.size());
        assertEquals("你好", map.get("Hello"));
        assertEquals("世界", map.get("World"));
    }

    @Test
    void clearRemovesCachedData() {
        cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
        cache.clear(1L, "doc-001");
        assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
    }

    @Test
    void getEntityMapReturnsUnmodifiableMap() {
        cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
        Map<String, String> map = cache.getEntityMap(1L, "doc-001");
        assertThrows(UnsupportedOperationException.class, () -> map.put("New", "新"));
    }
}
