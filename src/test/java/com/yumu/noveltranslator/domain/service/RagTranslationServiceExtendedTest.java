package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
import com.yumu.noveltranslator.domain.service.EmbeddingService;
import com.yumu.noveltranslator.domain.service.TranslationMemoryService;
import com.yumu.noveltranslator.domain.service.RagTranslationService;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.port.out.VectorStorePort;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
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
    private VectorStorePort vectorStorePort;

    private RagTranslationService service;

    @BeforeEach
    void setUp() {
        service = new RagTranslationService(embeddingService, translationMemoryService, vectorStorePort);
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
        com.yumu.noveltranslator.adapter.in.security.CustomUserDetails userDetails =
                new com.yumu.noveltranslator.adapter.in.security.CustomUserDetails(user);
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

            List<Map<String, String>> redisResult = buildVectorSearchResult(1,
                    "Hello", "你好", "0.05", "42");
            mockVectorSearch(redisResult);

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
            mockVectorSearch(Collections.emptyList());

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

            mockStoreVector();

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
            verify(vectorStorePort, never()).storeVector(anyString(), anyMap());
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

            mockStoreVector();

            invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google");

            verify(vectorStorePort).storeVector(eq("tm:99"), anyMap());
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

            mockStoreVector();

            invokeStoreToRedisVector("Hello", "你好", "zh", 1L, "google");

            verify(vectorStorePort).storeVector(argThat(key -> key.startsWith("tm:")), anyMap());
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

    private List<Map<String, String>> buildVectorSearchResult(long total, String sourceText, String targetText, String score, String id) {
        List<Map<String, String>> result = new ArrayList<>();
        if (total > 0) {
            Map<String, String> match = new LinkedHashMap<>();
            match.put("source_text", sourceText);
            match.put("target_text", targetText);
            match.put("score", score);
            match.put("id", id);
            result.add(match);
        }
        return result;
    }

    private void mockVectorSearch(List<Map<String, String>> result) {
        when(vectorStorePort.vectorSearch(any(float[].class), anyLong(), anyString(), anyList(), anyInt()))
                .thenReturn(result);
    }

    private void mockStoreVector() {
        doNothing().when(vectorStorePort).storeVector(anyString(), anyMap());
    }
}
