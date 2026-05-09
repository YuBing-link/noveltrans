package com.yumu.noveltranslator.adapter.out.redis;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlossaryCacheInvalidationTest {

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
        lenient().when(cacheVersionService.getVersion(anyString(), anyString())).thenReturn("1");
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Nested
    @DisplayName("反向索引维护 - buildReverseIndex")
    class ReverseIndexTests {

        @Test
        void buildReverseIndexExtractsWordsAndSadd() {
            when(setOperations.add(anyString(), anyString())).thenReturn(1L);
            lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            cacheService.buildReverseIndex("test-key", "The Apple is delicious and the Apple pie too");

            verify(setOperations, atLeastOnce()).add(eq("glossary:cache_keys:apple"), eq("test-key"));
            verify(setOperations, atLeastOnce()).add(eq("glossary:cache_keys:delicious"), eq("test-key"));
            verify(setOperations, atLeastOnce()).add(eq("glossary:cache_keys:the"), eq("test-key"));
            verify(stringRedisTemplate, atLeastOnce()).expire(startsWith("glossary:cache_keys:"), any(Duration.class));
        }

        @Test
        void buildReverseIndexIgnoresShortWords() {
            // 单词长度 < 3 的不应被索引
            when(valueOperations.get(anyString())).thenReturn(null);
            when(translationCacheMapper.selectById(anyString())).thenReturn(null);
            when(translationCacheMapper.selectOne(any())).thenReturn(null);
            when(setOperations.add(anyString(), anyString())).thenReturn(1L);
            lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            cacheService.putCache("test-key", "A an is be at", "translated", "en", "zh", "google");

            // 这些短词不应该被索引
            verify(setOperations, never()).add(eq("glossary:cache_keys:a"), anyString());
            verify(setOperations, never()).add(eq("glossary:cache_keys:an"), anyString());
            verify(setOperations, never()).add(eq("glossary:cache_keys:is"), anyString());
            verify(setOperations, never()).add(eq("glossary:cache_keys:be"), anyString());
            verify(setOperations, never()).add(eq("glossary:cache_keys:at"), anyString());
        }

        @Test
        void buildReverseIndexHandlesNullSourceText() {
            // 空源文本不应抛出异常
            cacheService.buildReverseIndex("test-key", null);
            cacheService.buildReverseIndex("test-key", "");
            cacheService.buildReverseIndex("test-key", "   ");

            verify(setOperations, never()).add(anyString(), anyString());
        }

        @Test
        void buildReverseIndexDeduplicatesWords() {
            when(setOperations.add(anyString(), anyString())).thenReturn(1L);
            lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            cacheService.buildReverseIndex("v1:test-key", "Apple Apple Apple");

            // 验证 apple 只被 SADD 了一次（虽然有三次出现）
            verify(setOperations, times(1)).add(eq("glossary:cache_keys:apple"), eq("v1:test-key"));
        }
    }

    @Nested
    @DisplayName("术语失效 - invalidateKeysForTerm")
    class InvalidationTests {

        @Test
        void invalidateKeysForTermDeletesAffectedKeysFromAllLayers() {
            // 模拟: "apple" 术语关联了 2 个缓存键
            Set<String> affectedKeys = Set.of("v1:hello apple world", "v1:eat an apple");
            when(setOperations.members("glossary:cache_keys:apple")).thenReturn(affectedKeys);
            lenient().when(stringRedisTemplate.delete(any(List.class))).thenReturn(2L);
            lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
            when(translationCacheMapper.delete(any())).thenReturn(1);

            cacheService.invalidateKeysForTerm("Apple");

            // 验证通过 SMEMBERS 获取了缓存键
            verify(setOperations).members("glossary:cache_keys:apple");

            // 验证清空了 L1 Caffeine 缓存
            // (无法直接验证 Caffeine invalidate，但后续 getCache 应返回 null)

            // 验证删除了 L2 Redis 缓存键 (实现使用 List.of)
            verify(stringRedisTemplate).delete((java.util.List<String>) argThat(obj -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> keys = (java.util.List<String>) obj;
                    return keys != null &&
                    keys.contains("translator:cache:v1:hello apple world") &&
                    keys.contains("translator:cache:v1:eat an apple");
            }));

            // 验证删除了 L3 数据库记录
            verify(translationCacheMapper, times(2)).delete(any());

            // 验证清理了反向索引 Set
            verify(stringRedisTemplate).delete("glossary:cache_keys:apple");
        }

        @Test
        void invalidateKeysForTermDoesNotAffectUnrelatedKeys() {
            // "apple" 术语不应影响包含 "orange" 的缓存键
            Set<String> affectedKeys = Set.of("v1:hello apple world");
            when(setOperations.members("glossary:cache_keys:apple")).thenReturn(affectedKeys);
            lenient().when(stringRedisTemplate.delete(any(List.class))).thenReturn(1L);
            when(translationCacheMapper.delete(any())).thenReturn(1);

            cacheService.invalidateKeysForTerm("apple");

            // 只应删除 "apple" 相关的键，不应触碰 "orange"
            verify(setOperations).members("glossary:cache_keys:apple");
            verify(setOperations, never()).members(contains("orange"));
        }

        @Test
        void invalidateKeysForTermHandlesNoMatchingKeys() {
            // 没有匹配的缓存键时应正常返回
            when(setOperations.members("glossary:cache_keys:nonexistent")).thenReturn(Set.of());

            cacheService.invalidateKeysForTerm("nonexistent");

            verify(setOperations).members("glossary:cache_keys:nonexistent");
            verify(stringRedisTemplate, never()).delete(any(List.class));
            verify(translationCacheMapper, never()).delete(any());
        }

        @Test
        void invalidateKeysForTermHandlesNullInput() {
            // 空输入不应抛出异常
            cacheService.invalidateKeysForTerm(null);
            cacheService.invalidateKeysForTerm("");
            cacheService.invalidateKeysForTerm("   ");

            verifyNoInteractions(setOperations);
        }

        @Test
        void invalidateKeysForTermIsCaseInsensitive() {
            // "Apple"、"APPLE"、"apple" 都应查找同一个小写 key
            Set<String> affectedKeys = Set.of("v1:some key");
            when(setOperations.members("glossary:cache_keys:apple")).thenReturn(affectedKeys);
            lenient().when(stringRedisTemplate.delete(any(List.class))).thenReturn(1L);
            when(translationCacheMapper.delete(any())).thenReturn(1);

            cacheService.invalidateKeysForTerm("APPLE");

            verify(setOperations).members("glossary:cache_keys:apple");

            // 再次用不同大小写调用，应使用相同的 key
            cacheService.invalidateKeysForTerm("aPpLe");

            verify(setOperations, times(2)).members("glossary:cache_keys:apple");
        }
    }

    @Nested
    @DisplayName("集成场景：putCache + invalidateKeysForTerm")
    class IntegrationScenarios {

        @Test
        void endToEnd_termAddedOnlyInvalidatesRelatedCache() {
            // 场景：建立反向索引，然后术语变更触发失效
            // 1. 直接建立反向索引（避免异步线程干扰 mock）
            when(setOperations.add(anyString(), anyString())).thenReturn(1L);
            lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            cacheService.buildReverseIndex("v1:apple-text", "Apple is a fruit");

            // 2. 术语变更：触发 invalidateKeysForTerm("Apple")
            Set<String> affectedKeys = Set.of("v1:apple-text");
            when(setOperations.members("glossary:cache_keys:apple")).thenReturn(affectedKeys);
            lenient().when(stringRedisTemplate.delete(any(List.class))).thenReturn(1L);
            when(translationCacheMapper.delete(any())).thenReturn(1);

            cacheService.invalidateKeysForTerm("Apple");

            // 3. 验证只删除了 "apple" 相关的索引
            verify(setOperations).members("glossary:cache_keys:apple");
            // "fruit" 相关的索引未被触碰
            verify(setOperations, never()).members(contains("fruit"));
        }

        @Test
        void reverseIndexMaintainsCorrectTtl() {
            // 验证反向索引 Set 的 TTL 为 24 小时
            when(setOperations.add(anyString(), anyString())).thenReturn(1L);
            lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

            cacheService.buildReverseIndex("test-key", "Testing word boundary");

            verify(stringRedisTemplate).expire(
                    eq("glossary:cache_keys:testing"),
                    eq(Duration.ofSeconds(24 * 3600))
            );
            verify(stringRedisTemplate).expire(
                    eq("glossary:cache_keys:word"),
                    eq(Duration.ofSeconds(24 * 3600))
            );
            verify(stringRedisTemplate).expire(
                    eq("glossary:cache_keys:boundary"),
                    eq(Duration.ofSeconds(24 * 3600))
            );
        }
    }
}
