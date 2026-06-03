package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheKeyUtil 单元测试
 */
class CacheKeyUtilTest {

    @Test
    void sameInputProducesSameKey() {
        String key1 = CacheKeyUtil.buildCacheKey("hello", "zh", "google");
        String key2 = CacheKeyUtil.buildCacheKey("hello", "zh", "google");
        assertEquals(key1, key2);
    }

    @Test
    void differentTextProducesDifferentKey() {
        String key1 = CacheKeyUtil.buildCacheKey("hello", "zh", "google");
        String key2 = CacheKeyUtil.buildCacheKey("world", "zh", "google");
        assertNotEquals(key1, key2);
    }

    @Test
    void differentLangProducesDifferentKey() {
        String key1 = CacheKeyUtil.buildCacheKey("hello", "zh", "google");
        String key2 = CacheKeyUtil.buildCacheKey("hello", "en", "google");
        assertNotEquals(key1, key2);
    }

    @Test
    void differentEngineProducesDifferentKey() {
        String key1 = CacheKeyUtil.buildCacheKey("hello", "zh", "google");
        String key2 = CacheKeyUtil.buildCacheKey("hello", "zh", "deepl");
        assertNotEquals(key1, key2);
    }

    @Test
    void keyIsMd5Format() {
        String key = CacheKeyUtil.buildCacheKey("test", "zh", "google");
        // 仅返回 32 位十六进制 MD5，无版本号前缀
        assertTrue(key.matches("[a-f0-9]{32}"));
    }

    @Test
    void versionedKeyIsDeprecatedReturnsBareMd5() {
        String key = CacheKeyUtil.buildCacheKey("test", "en", "zh", "2");
        // 已废弃，现在返回裸 MD5
        assertTrue(key.matches("[a-f0-9]{32}"));
    }
}
