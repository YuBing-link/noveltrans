package com.yumu.noveltranslator.service.pipeline;

import com.yumu.noveltranslator.dto.ConsistencyTranslationResult;
import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.service.EntityConsistencyService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationCacheService;
import com.yumu.noveltranslator.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.service.UserLevelThrottledTranslationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TranslationPipeline 测试")
class TranslationPipelineTest {

    private TranslationCacheService cacheService;
    private RagTranslationService ragTranslationService;
    private EntityConsistencyService entityConsistencyService;
    private UserLevelThrottledTranslationClient translationClient;
    private TranslationPostProcessingService postProcessingService;
    private TranslationPipeline pipeline;

    @BeforeEach
    void setUp() {
        cacheService = mock(TranslationCacheService.class);
        ragTranslationService = mock(RagTranslationService.class);
        entityConsistencyService = mock(EntityConsistencyService.class);
        translationClient = mock(UserLevelThrottledTranslationClient.class);
        postProcessingService = mock(TranslationPostProcessingService.class);
        pipeline = new TranslationPipeline(cacheService, ragTranslationService, entityConsistencyService, translationClient, postProcessingService, 1L, "doc-001");
    }

    @Nested
    @DisplayName("shouldCache 静态方法")
    class ShouldCacheTests {

        @Test
        void null原文不缓存() {
            assertFalse(TranslationPipeline.shouldCache(null, "译文"));
        }

        @Test
        void null译文不缓存() {
            assertFalse(TranslationPipeline.shouldCache("原文", null));
        }

        @Test
        void 原文译文相等不缓存() {
            assertFalse(TranslationPipeline.shouldCache("Hello", "Hello"));
        }

        @Test
        void 原文译文忽略大小写相等不缓存() {
            assertFalse(TranslationPipeline.shouldCache("Hello", "hello"));
        }

        @Test
        void 原文译文不同则缓存() {
            assertTrue(TranslationPipeline.shouldCache("Hello", "你好"));
        }
    }

    @Nested
    @DisplayName("isValidTranslation 静态方法")
    class IsValidTranslationTests {

        @Test
        void null原文返回false() {
            assertFalse(TranslationPipeline.isValidTranslation(null, "译文"));
        }

        @Test
        void null译文返回false() {
            assertFalse(TranslationPipeline.isValidTranslation("原文", null));
        }

        @Test
        void 包含广告关键词返回false() {
            assertFalse(TranslationPipeline.isValidTranslation("Hello", "人工智能助手生成的内容"));
        }

        @Test
        void 包含Gemini关键词返回false() {
            assertFalse(TranslationPipeline.isValidTranslation("Hello", "由 Gemini 提供"));
        }

        @Test
        void 译文长度超过原文10倍返回false() {
            String shortText = "Hi";
            String longResult = "A".repeat(shortText.length() * 10 + 1);
            assertFalse(TranslationPipeline.isValidTranslation(shortText, longResult));
        }

        @Test
        void 正常翻译返回true() {
            assertTrue(TranslationPipeline.isValidTranslation("Hello World", "你好世界"));
        }
    }

    @Nested
    @DisplayName("execute 完整管线")
    class ExecuteTests {

        @Test
        void L1缓存命中() {
            when(cacheService.getCache(anyString())).thenReturn("缓存结果");

            String result = pipeline.execute("Hello", "zh", "google");

            assertEquals("缓存结果", result);
            verify(cacheService, never()).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void L2RAG直接命中() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(true);
            ragResp.setTranslation("RAG翻译结果");
            ragResp.setSimilarity(0.95);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("RAG翻译结果");

            String result = pipeline.execute("Hello", "zh", "google");

            assertEquals("RAG翻译结果", result);
            verify(cacheService).putCache(anyString(), eq("Hello"), eq("RAG翻译结果"), eq("auto"), eq("zh"), eq("google"));
        }

        @Test
        void L3实体一致性应用() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(true);
            ConsistencyTranslationResult consistencyResult = new ConsistencyTranslationResult();
            consistencyResult.setConsistencyApplied(true);
            consistencyResult.setTranslatedText("一致性翻译结果");
            when(entityConsistencyService.translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString())).thenReturn(consistencyResult);
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("一致性翻译结果");

            String result = pipeline.execute("Hello World this is a long text", "zh", "google");

            assertEquals("一致性翻译结果", result);
            verify(cacheService).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void L3一致性未应用走L4() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(true);
            ConsistencyTranslationResult consistencyResult = new ConsistencyTranslationResult();
            consistencyResult.setConsistencyApplied(false);
            when(entityConsistencyService.translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString())).thenReturn(consistencyResult);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("{\"data\":\"L4翻译结果\"}");
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("L4翻译结果");

            String result = pipeline.execute("Hello World this is a long text", "zh", "google");

            assertEquals("L4翻译结果", result);
        }

        @Test
        void L4直译成功() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("{\"data\":\"你好\"}");
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("你好");

            String result = pipeline.execute("Hello", "zh", "google");

            assertEquals("你好", result);
            verify(cacheService).putCache(anyString(), eq("Hello"), eq("你好"), eq("auto"), eq("zh"), eq("google"));
        }

        @Test
        void L4返回null() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("{\"code\":500,\"error\":\"服务不可用\"}");

            String result = pipeline.execute("Hello", "zh", "google");

            assertNull(result);
        }

        @Test
        void L4返回广告关键词被拒绝() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("{\"data\":\"Google AI 人工智能助手\"}");

            String result = pipeline.execute("Hello", "zh", "google");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("executeFast 快速模式")
    class ExecuteFastTests {

        @Test
        void 缓存命中() {
            when(cacheService.getCache(anyString())).thenReturn("快速缓存");

            String result = pipeline.executeFast("Hello", "zh", "google");

            assertEquals("快速缓存", result);
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean());
        }

        @Test
        void 直译成功() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn("{\"data\":\"快速翻译\"}");
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("快速翻译");

            String result = pipeline.executeFast("Hello", "zh", "google");

            assertEquals("快速翻译", result);
        }

        @Test
        void 翻译失败返回原文() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn("{\"code\":500}");

            String result = pipeline.executeFast("Hello", "zh", "google");

            assertEquals("Hello", result);
        }

        @Test
        void 翻译异常返回原文() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenThrow(new RuntimeException("连接失败"));

            String result = pipeline.executeFast("Hello", "zh", "google");

            assertEquals("Hello", result);
        }

        @Test
        void 广告关键词返回原文() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn("{\"data\":\"Google AI 助手\"}");

            String result = pipeline.executeFast("Hello", "zh", "google");

            assertEquals("Hello", result);
        }

        @Test
        void html模式() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean())).thenReturn("{\"data\":\"<p>翻译</p>\"}");
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("<p>翻译</p>");

            String result = pipeline.executeFast("<p>Hello</p>", "zh", "google", true);

            assertEquals("<p>翻译</p>", result);
            verify(translationClient).translate(anyString(), anyString(), anyString(), eq(false), eq(true));
        }
    }

    @Nested
    @DisplayName("userId为null时的行为")
    class NullUserIdTests {

        @Test
        void userId为null时跳过L3一致性() {
            TranslationPipeline nullUserPipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService, translationClient, postProcessingService, null, null);

            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            ragResp.setDirectHit(false);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString())).thenReturn(ragResp);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("{\"data\":\"L4结果\"}");
            when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString())).thenReturn("L4结果");

            String result = nullUserPipeline.execute("Hello", "zh", "google");

            assertEquals("L4结果", result);
            verify(entityConsistencyService, never()).translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString());
        }
    }
}
