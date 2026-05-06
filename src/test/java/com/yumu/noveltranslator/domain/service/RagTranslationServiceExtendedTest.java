package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.TranslationMemory;
import com.yumu.noveltranslator.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagTranslationService 补充测试
 * 覆盖现有测试未覆盖的方法：searchSimilarWithUser, storeTranslationMemory(Long userId),
 * cosineSimilarity, formatVectorForRedis, searchFallback happy path, rejectQuality 缺失分支
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RagTranslationService 补充测试")
class RagTranslationServiceExtendedTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private TranslationMemoryService translationMemoryService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RagTranslationService service;

    @BeforeEach
    void setUp() {
        service = new RagTranslationService(embeddingService, translationMemoryService, stringRedisTemplate);
        ReflectionTestUtils.setField(service, "directHitThreshold", 0.85);
        ReflectionTestUtils.setField(service, "referenceThreshold", 0.5);
        ReflectionTestUtils.setField(service, "knnTopK", 5);
        ReflectionTestUtils.setField(service, "fallbackMysqlLimit", 20);
        ReflectionTestUtils.setField(service, "provider", "openai");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId) {
        User user = new User();
        user.setId(userId);
        com.yumu.noveltranslator.security.CustomUserDetails userDetails =
                new com.yumu.noveltranslator.security.CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // ============ searchSimilarWithUser 测试 ============

    @Nested
    @DisplayName("searchSimilarWithUser - 指定userId查询")
    class SearchSimilarWithUserTests {

        @Test
        void userId为null返回空响应() throws Exception {
            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "Hello", "zh", "ai-team", null);
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 文本为null返回空响应() throws Exception {
            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    null, "zh", "ai-team", 1L);
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 文本空白返回空响应() throws Exception {
            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "   ", "zh", "ai-team", 1L);
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 向量为空返回空响应() throws Exception {
            when(embeddingService.embed("Hello")).thenReturn(new float[0]);
            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "Hello", "zh", "ai-team", 1L);
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void Redis命中直接返回() throws Exception {
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingService.embed("Hello")).thenReturn(vec);

            List<Object> redisResult = buildRedisResult(1,
                    "Hello", "你好", "0.05", "42");
            mockRedisExecute(redisResult);

            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "Hello", "zh", "ai-team", 1L);

            assertTrue(response.isDirectHit());
            assertEquals("你好", response.getTranslation());
            assertEquals(0.95, response.getSimilarity(), 0.01);
        }

        @Test
        void Redis无结果走MySQL降级() throws Exception {
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingService.embed("Hello")).thenReturn(vec);
            mockRedisExecute(Collections.emptyList());

            TranslationMemory mem = new TranslationMemory();
            mem.setId(10L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            mem.setEmbedding(Arrays.asList(0.1f, 0.2f, 0.3f));
            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(20)))
                    .thenReturn(List.of(mem));

            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "Hello", "zh", "ai-team", 1L);

            // embedding 完全一致 → similarity ≈ 1.0
            assertTrue(response.isDirectHit());
        }

        @Test
        void 异常返回空响应() throws Exception {
            when(embeddingService.embed("Hello")).thenThrow(new RuntimeException("vector error"));
            RagTranslationResponse response = invokeSearchSimilarWithUser(
                    "Hello", "zh", "ai-team", 1L);
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        private RagTranslationResponse invokeSearchSimilarWithUser(
                String text, String target, String engine, Long userId) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("searchSimilarWithUser",
                    String.class, String.class, String.class, Long.class);
            m.setAccessible(true);
            return (RagTranslationResponse) m.invoke(service, text, target, engine, userId);
        }
    }

    // ============ storeTranslationMemory(Long userId) 测试 ============

    @Nested
    @DisplayName("storeTranslationMemory(Long userId) - 指定用户存储")
    class StoreTranslationMemoryWithUserTests {

        @Test
        void userId为null直接返回() throws Exception {
            invokeStoreMemoryWithUser("Hello", "你好", "zh", "google", null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 原文为null直接返回() throws Exception {
            invokeStoreMemoryWithUser(null, "你好", "zh", "google", 1L);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 质量不合格被拒绝() throws Exception {
            invokeStoreMemoryWithUser("Hello", "Hello", "zh", "google", 1L);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 质量通过则存储() throws Exception {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            invokeStoreMemoryWithUser("Hello", "你好世界", "zh", "ai-team", 1L);

            verify(translationMemoryService).storeTranslation(
                    eq("Hello"), eq("你好世界"), eq("auto"), eq("zh"), eq(1L), isNull(), eq("ai-team"), isNull());
        }

        @Test
        void 异常被捕获不抛出() throws Exception {
            doThrow(new RuntimeException("DB error")).when(translationMemoryService).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
            assertDoesNotThrow(() -> invokeStoreMemoryWithUser("Hello", "你好世界", "zh", "ai-team", 1L));
            assertDoesNotThrow(() -> invokeStoreMemoryWithUser("Hello", "你好世界", "zh", "ai-team", 1L));
        }

        private void invokeStoreMemoryWithUser(
                String source, String target, String targetLang, String engine, Long userId) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("storeTranslationMemory",
                    String.class, String.class, String.class, String.class, Long.class);
            m.setAccessible(true);
            m.invoke(service, source, target, targetLang, engine, userId);
        }
    }

    // ============ cosineSimilarity 测试 ============

    @Nested
    @DisplayName("cosineSimilarity - 余弦相似度计算")
    class CosineSimilarityTests {

        @Test
        void 完全相同向量返回1() throws Exception {
            float[] a = {1.0f, 0.0f, 0.0f};
            List<Float> b = Arrays.asList(1.0f, 0.0f, 0.0f);
            assertEquals(1.0, invokeCosineSimilarity(a, b), 0.001);
        }

        @Test
        void 正交向量返回0() throws Exception {
            float[] a = {1.0f, 0.0f, 0.0f};
            List<Float> b = Arrays.asList(0.0f, 1.0f, 0.0f);
            assertEquals(0.0, invokeCosineSimilarity(a, b), 0.001);
        }

        void 反向向量返回负1() throws Exception {
            float[] a = {1.0f, 0.0f, 0.0f};
            List<Float> b = Arrays.asList(-1.0f, 0.0f, 0.0f);
            assertEquals(-1.0, invokeCosineSimilarity(a, b), 0.001);
        }

        @Test
        void 维度不匹配返回0() throws Exception {
            float[] a = {1.0f, 0.0f};
            List<Float> b = Arrays.asList(1.0f, 0.0f, 0.0f);
            assertEquals(0.0, invokeCosineSimilarity(a, b), 0.001);
        }

        @Test
        void 零向量返回0() throws Exception {
            float[] a = {0.0f, 0.0f, 0.0f};
            List<Float> b = Arrays.asList(1.0f, 2.0f, 3.0f);
            assertEquals(0.0, invokeCosineSimilarity(a, b), 0.001);
        }

        @Test
        void 正常计算相似度() throws Exception {
            float[] a = {1.0f, 2.0f, 3.0f};
            List<Float> b = Arrays.asList(1.0f, 2.0f, 3.0f);
            double result = invokeCosineSimilarity(a, b);
            assertEquals(1.0, result, 0.001);
        }

        private double invokeCosineSimilarity(float[] a, List<Float> b) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("cosineSimilarity", float[].class, List.class);
            m.setAccessible(true);
            return (Double) m.invoke(service, a, b);
        }
    }

    // ============ formatVectorForRedis 测试 ============

    @Nested
    @DisplayName("formatVectorForRedis - 向量格式化")
    class FormatVectorForRedisTests {

        @Test
        void 单元素向量() throws Exception {
            assertEquals("1,0.500000", invokeFormatVector(new float[]{0.5f}));
        }

        @Test
        void 多元素向量() throws Exception {
            String result = invokeFormatVector(new float[]{0.1f, 0.2f, 0.3f});
            assertEquals("3,0.100000,0.200000,0.300000", result);
        }

        @Test
        void 负数向量() throws Exception {
            String result = invokeFormatVector(new float[]{-0.5f, 0.5f});
            assertEquals("2,-0.500000,0.500000", result);
        }

        private String invokeFormatVector(float[] vec) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("formatVectorForRedis", float[].class);
            m.setAccessible(true);
            return (String) m.invoke(service, vec);
        }
    }

    // ============ rejectQuality 缺失分支测试 ============

    @Nested
    @DisplayName("rejectQuality - 缺失分支覆盖")
    class RejectQualityExtendedTests {

        @Test
        void 长度比率过低被拒绝() throws Exception {
            // 译文不足原文 10%
            String result = invokeRejectQuality("This is a very long sentence with many words", "短");
            assertNotNull(result);
            assertTrue(result.startsWith("length_ratio_too_low"));
        }

        @Test
        void 译文为null被拒绝() throws Exception {
            String result = invokeRejectQuality("Hello", null);
            assertEquals("empty_target", result);
        }

        @Test
        void 译文空白被拒绝() throws Exception {
            String result = invokeRejectQuality("Hello", "   ");
            assertEquals("empty_target", result);
        }

        @Test
        void 长度比率过高被拒绝() throws Exception {
            String result = invokeRejectQuality("Hi", "这是一段非常非常非常非常非常非常非常非常非常非常长的翻译文字内容");
            assertNotNull(result);
            assertTrue(result.startsWith("length_ratio_too_high"));
        }

        @Test
        void 质量通过返回null() throws Exception {
            String result = invokeRejectQuality("Hello world", "你好世界");
            assertNull(result);
        }

        private String invokeRejectQuality(String source, String target) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("rejectQuality", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, source, target);
        }
    }

    // ============ searchFallback happy path 测试 ============

    @Nested
    @DisplayName("searchFallback - MySQL降级正常路径")
    class SearchFallbackTests {

        @Test
        void 无翻译记忆返回空列表() throws Exception {
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            List<?> result = invokeSearchFallback(new float[]{0.1f, 0.2f, 0.3f}, 1L, "zh");
            assertTrue(result.isEmpty());
        }

        @Test
        void embedding为null的记忆被跳过() throws Exception {
            TranslationMemory mem = new TranslationMemory();
            mem.setId(1L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            mem.setEmbedding(null);

            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(List.of(mem));

            List<?> result = invokeSearchFallback(new float[]{0.1f, 0.2f, 0.3f}, 1L, "zh");
            assertTrue(result.isEmpty());
        }

        @Test
        void embedding为空的记忆被跳过() throws Exception {
            TranslationMemory mem = new TranslationMemory();
            mem.setId(1L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            mem.setEmbedding(new ArrayList<>());

            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(List.of(mem));

            List<?> result = invokeSearchFallback(new float[]{0.1f, 0.2f, 0.3f}, 1L, "zh");
            assertTrue(result.isEmpty());
        }

        @Test
        void 相似度超过阈值的记忆被返回() throws Exception {
            TranslationMemory mem = new TranslationMemory();
            mem.setId(10L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            // 完全相同的 embedding → similarity ≈ 1.0 > 0.5
            mem.setEmbedding(Arrays.asList(0.1f, 0.2f, 0.3f));

            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(20)))
                    .thenReturn(List.of(mem));

            List<?> result = invokeSearchFallback(new float[]{0.1f, 0.2f, 0.3f}, 1L, "zh");
            assertEquals(1, result.size());
        }

        @Test
        void 相似度低于阈值的记忆被过滤() throws Exception {
            TranslationMemory mem = new TranslationMemory();
            mem.setId(10L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            // 正交向量 → similarity ≈ 0.0 < 0.5
            mem.setEmbedding(Arrays.asList(0.0f, 1.0f, 0.0f));

            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(20)))
                    .thenReturn(List.of(mem));

            List<?> result = invokeSearchFallback(new float[]{1.0f, 0.0f, 0.0f}, 1L, "zh");
            assertTrue(result.isEmpty());
        }

        @Test
        void 查询异常返回空列表() throws Exception {
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("DB connection lost"));

            List<?> result = invokeSearchFallback(new float[]{0.1f}, 1L, "zh");
            assertTrue(result.isEmpty());
        }

        @Test
        void 多条结果按相似度排序() throws Exception {
            TranslationMemory mem1 = new TranslationMemory();
            mem1.setId(1L);
            mem1.setSourceText("A");
            mem1.setTargetText("甲");
            mem1.setEmbedding(Arrays.asList(0.1f, 0.2f, 0.4f)); // 较不相似

            TranslationMemory mem2 = new TranslationMemory();
            mem2.setId(2L);
            mem2.setSourceText("B");
            mem2.setTargetText("乙");
            mem2.setEmbedding(Arrays.asList(0.1f, 0.2f, 0.3f)); // 完全相同

            float[] query = {0.1f, 0.2f, 0.3f};
            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(20)))
                    .thenReturn(List.of(mem1, mem2));

            List<?> result = invokeSearchFallback(query, 1L, "zh");
            assertEquals(2, result.size());
            // 第一个应该是最相似的那个 (mem2)
            RagTranslationResponse.RagMatch best = (RagTranslationResponse.RagMatch) result.get(0);
            assertEquals(2L, best.getMemoryId());
        }

        private List<?> invokeSearchFallback(float[] queryVector, Long userId, String targetLang) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("searchFallback",
                    float[].class, Long.class, String.class, List.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(service, queryVector, userId, targetLang, List.of("team", "expert", "fast"));
        }
    }

    // ============ parseSearchResult 边界测试 ============

    @Nested
    @DisplayName("parseSearchResult - 解析边界情况")
    class ParseSearchResultTests {

        @Test
        void null结果返回空列表() throws Exception {
            List<?> result = invokeParseSearchResult(null);
            assertTrue(result.isEmpty());
        }

        @Test
        void 非List结果返回空列表() throws Exception {
            List<?> result = invokeParseSearchResult("not a list");
            assertTrue(result.isEmpty());
        }

        @Test
        void 空List返回空列表() throws Exception {
            List<?> result = invokeParseSearchResult(new ArrayList<>());
            assertTrue(result.isEmpty());
        }

        @Test
        void score解析失败跳过该条目() throws Exception {
            List<Object> result = new ArrayList<>();
            result.add(1L);
            result.add("tm:1".getBytes(StandardCharsets.UTF_8));
            List<byte[]> fields = new ArrayList<>();
            fields.add("source_text".getBytes(StandardCharsets.UTF_8));
            fields.add("Hello".getBytes(StandardCharsets.UTF_8));
            fields.add("target_text".getBytes(StandardCharsets.UTF_8));
            fields.add("你好".getBytes(StandardCharsets.UTF_8));
            fields.add("score".getBytes(StandardCharsets.UTF_8));
            fields.add("not_a_number".getBytes(StandardCharsets.UTF_8));
            fields.add("id".getBytes(StandardCharsets.UTF_8));
            fields.add("1".getBytes(StandardCharsets.UTF_8));
            result.add(fields);

            List<?> matches = invokeParseSearchResult(result);
            assertTrue(matches.isEmpty()); // score 解析失败被 continue 跳过
        }

        @Test
        void 正常解析多条结果() throws Exception {
            List<Object> result = buildRedisResult(2, "Hello", "你好", "0.1", "1");
            // Add second document
            result.add("tm:2".getBytes(StandardCharsets.UTF_8));
            List<byte[]> fields2 = new ArrayList<>();
            fields2.add("source_text".getBytes(StandardCharsets.UTF_8));
            fields2.add("World".getBytes(StandardCharsets.UTF_8));
            fields2.add("target_text".getBytes(StandardCharsets.UTF_8));
            fields2.add("世界".getBytes(StandardCharsets.UTF_8));
            fields2.add("score".getBytes(StandardCharsets.UTF_8));
            fields2.add("0.3".getBytes(StandardCharsets.UTF_8));
            fields2.add("id".getBytes(StandardCharsets.UTF_8));
            fields2.add("2".getBytes(StandardCharsets.UTF_8));
            result.add(fields2);

            List<?> matches = invokeParseSearchResult(result);
            assertEquals(2, matches.size());
            // 按相似度排序 (score 0.1 → sim 0.9 first, score 0.3 → sim 0.7 second)
            RagTranslationResponse.RagMatch first = (RagTranslationResponse.RagMatch) matches.get(0);
            assertEquals(1L, first.getMemoryId());
            assertEquals(0.9, first.getSimilarity(), 0.01);
        }

        private List<?> invokeParseSearchResult(Object result) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("parseSearchResult", Object.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(service, result);
        }
    }

    // ============ storeToRedisVector 测试 ============

    @Nested
    @DisplayName("storeToRedisVector - Redis向量存储")
    class StoreToRedisVectorTests {

        @Test
        void embedding为空不存储() throws Exception {
            when(embeddingService.embed(anyString())).thenReturn(new float[0]);
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google");
            verify(stringRedisTemplate, never()).opsForHash();
        }

        @Test
        void 找到匹配记忆使用其ID() throws Exception {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            TranslationMemory mem = new TranslationMemory();
            mem.setId(99L);
            mem.setSourceText("Hello");
            mem.setTargetText("你好");
            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(1)))
                    .thenReturn(List.of(mem));

            HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google");

            verify(hashOps).put(eq("tm:99"), eq("id"), eq("99"));
        }

        @Test
        void 无匹配记忆使用UUID() throws Exception {
            when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            TranslationMemory mem = new TranslationMemory();
            mem.setId(99L);
            mem.setSourceText("Different text");
            mem.setTargetText("不同的文字");
            when(translationMemoryService.searchByUserAndLang(eq(1L), eq("auto"), eq("zh"), eq(1)))
                    .thenReturn(List.of(mem));

            HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
            when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);

            invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google");

            // 应该使用 UUID key
            verify(hashOps).put(argThat(key -> key.startsWith("tm:")), eq("id"), eq("0"));
        }

        @Test
        void 异常被捕获不抛出() throws Exception {
            when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embed failed"));

            assertDoesNotThrow(() -> invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google"));
        }

        private void invokeStoreToRedisVector(String source, String target, String targetLang, Long userId, String engine) throws Exception {
            Method m = RagTranslationService.class.getDeclaredMethod("storeToRedisVector",
                    String.class, String.class, String.class, Long.class, String.class, String.class);
            m.setAccessible(true);
            m.invoke(service, source, target, targetLang, userId, engine, "fast");
        }
    }

    // ============ 辅助方法 ============

    private List<Object> buildRedisResult(long total, String sourceText, String targetText, String score, String id) {
        List<Object> result = new ArrayList<>();
        result.add(total);
        if (total > 0) {
            result.add(("tm:" + id).getBytes(StandardCharsets.UTF_8));
            List<byte[]> fieldArray = new ArrayList<>();
            fieldArray.add("source_text".getBytes(StandardCharsets.UTF_8));
            fieldArray.add(sourceText.getBytes(StandardCharsets.UTF_8));
            fieldArray.add("target_text".getBytes(StandardCharsets.UTF_8));
            fieldArray.add(targetText.getBytes(StandardCharsets.UTF_8));
            fieldArray.add("score".getBytes(StandardCharsets.UTF_8));
            fieldArray.add(score.getBytes(StandardCharsets.UTF_8));
            fieldArray.add("id".getBytes(StandardCharsets.UTF_8));
            fieldArray.add(id.getBytes(StandardCharsets.UTF_8));
            result.add(fieldArray);
        }
        return result;
    }

    private void mockRedisExecute(List<Object> result) {
        doAnswer(invocation -> {
            org.springframework.data.redis.core.RedisCallback<Object> callback = invocation.getArgument(0);
            return result;
        }).when(stringRedisTemplate).execute(any(org.springframework.data.redis.core.RedisCallback.class));
    }
}
