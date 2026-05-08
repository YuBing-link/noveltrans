package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.TranslationMemoryService;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.port.out.EmbeddingPort;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagTranslationApplicationService 补充测试
 * 覆盖：cosineSimilarity, searchFallback happy path, rejectQuality 缺失分支
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RagTranslationApplicationService 补充测试")
class RagTranslationServiceExtendedTest {

    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private TranslationMemoryService translationMemoryService;
    @Mock
    private VectorStorePort vectorStorePort;

    private RagTranslationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RagTranslationApplicationService(embeddingPort, translationMemoryService, vectorStorePort);
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

    // ============ searchSimilarWithModes 测试 ============

    @Nested
    @DisplayName("searchSimilarWithModes - 指定userId查询")
    class SearchSimilarWithModesTests {

        @Test
        void userId为null返回空响应() {
            RagTranslationResponse response = service.searchSimilarWithModes(null, "Hello", "zh", List.of("team", "expert", "fast"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 文本为null返回空响应() {
            RagTranslationResponse response = service.searchSimilarWithModes(1L, null, "zh", List.of("team"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 文本空白返回空响应() {
            RagTranslationResponse response = service.searchSimilarWithModes(1L, "   ", "zh", List.of("team"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 向量为空返回空响应() {
            when(embeddingPort.embed("Hello")).thenReturn(new float[0]);
            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello", "zh", List.of("team"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void Redis命中直接返回() {
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingPort.embed("Hello")).thenReturn(vec);

            List<Map<String, String>> redisResult = buildVectorSearchResult(1,
                    "Hello", "你好", "0.05", "42");
            mockVectorSearch(redisResult);

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello", "zh", List.of("team"));

            assertTrue(response.isDirectHit());
            assertEquals("你好", response.getTranslation());
            assertEquals(0.95, response.getSimilarity(), 0.01);
        }

        @Test
        void 异常返回空响应() {
            when(embeddingPort.embed("Hello")).thenThrow(new RuntimeException("vector error"));
            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello", "zh", List.of("team"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }
    }

    // ============ storeTranslationMemory 测试 ============

    @Nested
    @DisplayName("storeTranslationMemory - 指定用户存储")
    class StoreTranslationMemoryTests {

        @Test
        void userId为null直接返回() {
            service.storeTranslationMemory("Hello", "你好", "zh", "google", null, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 原文为null直接返回() {
            service.storeTranslationMemory(null, "你好", "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 质量不合格被拒绝() {
            service.storeTranslationMemory("Hello", "Hello", "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 质量通过则存储() {
            when(embeddingPort.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            mockStoreVector();

            service.storeTranslationMemory("Hello", "你好世界", "zh", "ai-team", 1L, null);

            verify(translationMemoryService).storeTranslation(
                    eq("Hello"), eq("你好世界"), eq("auto"), eq("zh"), eq(1L), isNull(), eq("ai-team"), isNull());
        }

        @Test
        void 异常被捕获不抛出() {
            doThrow(new RuntimeException("DB error")).when(translationMemoryService).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
            assertDoesNotThrow(() -> service.storeTranslationMemory("Hello", "你好世界", "zh", "ai-team", 1L, null));
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

        private double invokeCosineSimilarity(float[] a, List<Float> b) throws Exception {
            Method m = RagTranslationApplicationService.class.getDeclaredMethod("cosineSimilarity", float[].class, List.class);
            m.setAccessible(true);
            return (Double) m.invoke(service, a, b);
        }
    }

    // ============ rejectQuality 测试 ============

    @Nested
    @DisplayName("rejectQuality - 缺失分支覆盖")
    class RejectQualityExtendedTests {

        @Test
        void 长度比率过低被拒绝() throws Exception {
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
            Method m = RagTranslationApplicationService.class.getDeclaredMethod("rejectQuality", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, source, target);
        }
    }

    // ============ searchFallback 测试 ============

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

        private List<?> invokeSearchFallback(float[] queryVector, Long userId, String targetLang) throws Exception {
            Method m = RagTranslationApplicationService.class.getDeclaredMethod("searchFallback",
                    float[].class, Long.class, String.class, List.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(service, queryVector, userId, targetLang, List.of("team", "expert", "fast"));
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
