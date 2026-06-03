package com.yumu.noveltranslator.domain.service.pipeline;
import com.yumu.noveltranslator.domain.service.TranslationPipeline;

import com.yumu.noveltranslator.port.dto.translation.ConsistencyTranslationResult;
import com.yumu.noveltranslator.port.dto.translation.EntityMapping;
import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.domain.model.Glossary;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.domain.service.EntityConsistencyService;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;
import com.yumu.noveltranslator.adapter.out.translate.TeamTranslationService;
import com.yumu.noveltranslator.port.out.TranslationCachePort;
import com.yumu.noveltranslator.domain.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.adapter.out.translate.UserLevelThrottledTranslationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationPipeline 扩展测试
 * 覆盖四级管线架构的所有分支路径，提高分支覆盖率。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationPipeline 扩展测试")
class TranslationPipelineExtendedTest {

    @Mock
    private TranslationCachePort cacheService;
    @Mock
    private RagTranslationApplicationService ragTranslationService;
    @Mock
    private EntityConsistencyService entityConsistencyService;
    @Mock
    private UserLevelThrottledTranslationClient translationClient;
    @Mock
    private TranslationPostProcessingService postProcessingService;
    @Mock
    private TeamTranslationService teamTranslationService;

    private TranslationPipeline pipeline;
    private TranslationPipeline teamPipeline;
    private TranslationPipeline pipelineWithGlossary;

    private static final String SOURCE_TEXT = "Hello World";
    private static final String TRANSLATED_TEXT = "你好世界";
    private static final String TARGET_LANG = "zh";
    private static final Long USER_ID = 42L;
    private static final String DOC_ID = "doc-001";
    private static final String SUCCESS_JSON = "{\"code\":200,\"data\":\"" + TRANSLATED_TEXT + "\"}";

    @BeforeEach
    void setUp() {
        // Standard pipeline (no team service, no glossary)
        pipeline = new TranslationPipeline(
                cacheService, ragTranslationService, entityConsistencyService,
                translationClient, postProcessingService,
                USER_ID, null, DOC_ID);

        // Team pipeline
        teamPipeline = new TranslationPipeline(
                cacheService, ragTranslationService, entityConsistencyService,
                translationClient, postProcessingService,
                teamTranslationService, USER_ID, null, DOC_ID);

        // Pipeline with glossary
        Glossary glossary = new Glossary();
        glossary.setSourceWord("Hello");
        glossary.setTargetWord("你好");
        pipelineWithGlossary = new TranslationPipeline(
                cacheService, ragTranslationService, entityConsistencyService,
                translationClient, postProcessingService,
                teamTranslationService, USER_ID, null, DOC_ID, List.of(glossary));

        // Common stubs — lenient so they don't interfere with test-specific stubs
        lenient().doReturn(Optional.empty()).when(cacheService).getCacheByMode(anyString(), anyString());
        lenient().doNothing().when(cacheService).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        RagTranslationResponse ragMiss = buildRagMiss();
        lenient().doReturn(ragMiss).when(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), anyList());
        lenient().doNothing().when(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());

        lenient().doAnswer(inv -> inv.getArgument(1)).when(postProcessingService).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());

        lenient().doReturn(false).when(entityConsistencyService).shouldUseConsistency(anyString());

        // Translation client: default success for any args
        lenient().doReturn(SUCCESS_JSON).when(translationClient)
                .translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList(), any(), any());

        lenient().doReturn("Team translated result")
                .when(teamTranslationService)
                .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());
    }

    // ======================== Helper methods ========================

    private static RagTranslationResponse buildRagMiss() {
        RagTranslationResponse r = new RagTranslationResponse();
        r.setDirectHit(false);
        r.setTranslation(null);
        r.setSimilarity(0.3);
        return r;
    }

    private static RagTranslationResponse buildRagHit(String translation) {
        RagTranslationResponse r = new RagTranslationResponse();
        r.setDirectHit(true);
        r.setTranslation(translation);
        r.setSimilarity(0.92);
        return r;
    }

    private static ConsistencyTranslationResult buildConsistencyResult(String translated, boolean applied) {
        ConsistencyTranslationResult r = new ConsistencyTranslationResult();
        r.setTranslatedText(translated);
        r.setConsistencyApplied(applied);
        return r;
    }

    /**
     * Helper to stub translateEntities which declares throws Exception.
     * Wrapping in a helper method satisfies the compiler's checked exception requirement.
     */
    @SuppressWarnings("unchecked")
    private void stubTranslateEntities(Map<String, String> result) throws Exception {
        doReturn(result).when(entityConsistencyService).translateEntities(anyList(), anyString());
    }

    // ======================== execute() — Full pipeline tests ========================

    @Nested
    @DisplayName("execute — 完整四级管线")
    class ExecuteTests {

        @Test
        @DisplayName("L1 缓存命中 — 直接返回缓存结果")
        void cacheHit() {
            lenient().doReturn(Optional.of(TRANSLATED_TEXT)).when(cacheService).getCacheByMode(anyString(), eq("fast"));

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L1 缓存未命中 — 走完整管线到 L4")
        void cacheMissFullPipeline() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L2 RAG 直接命中")
        void ragHit() {
            RagTranslationResponse ragHit = buildRagHit("RAG翻译结果");
            lenient().doReturn(ragHit).when(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), anyList());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals("RAG翻译结果", result);
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList(), any(), any());
            verify(cacheService).putCache(anyString(), anyString(), eq("RAG翻译结果"), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("L3 实体一致性命中")
        void entityConsistencyHit() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(anyString());
            ConsistencyTranslationResult consistencyResult = buildConsistencyResult("一致性翻译结果", true);
            lenient().doReturn(consistencyResult).when(entityConsistencyService)
                    .translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals("一致性翻译结果", result);
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L3 实体一致性未启用 — 降级到 L4")
        void entityConsistencyNotEnabled() {
            lenient().doReturn(false).when(entityConsistencyService).shouldUseConsistency(anyString());
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L3 实体一致性返回 applied=false — 降级到 L4")
        void entityConsistencyNotApplied() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(anyString());
            ConsistencyTranslationResult notApplied = buildConsistencyResult("some text", false);
            lenient().doReturn(notApplied).when(entityConsistencyService)
                    .translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString());
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L3 实体一致性返回 translatedText=null — 降级到 L4")
        void entityConsistencyTranslatedTextNull() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(anyString());
            ConsistencyTranslationResult nullText = buildConsistencyResult(null, true);
            lenient().doReturn(nullText).when(entityConsistencyService)
                    .translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString());
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("L4 直译成功")
        void l4DirectTranslationSuccess() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(cacheService).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("L4 直译返回 null — 整体返回 null")
        void l4ReturnsNull() {
            lenient().doReturn(null).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertNull(result);
        }

        @Test
        @DisplayName("L4 直译结果包含广告关键词 — 返回 null")
        void l4AdKeywords() {
            String adJson = "{\"code\":200,\"data\":\"人工智能助手很强大\"}";
            lenient().doReturn(adJson).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertNull(result);
        }

        @Test
        @DisplayName("L4 直译结果长度异常（超过原文 10 倍）— 返回 null")
        void l4AbnormalLength() {
            String longResult = "a".repeat(SOURCE_TEXT.length() * 10 + 1);
            String longJson = "{\"code\":200,\"data\":\"" + longResult + "\"}";
            lenient().doReturn(longJson).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertNull(result);
        }

        @Test
        @DisplayName("L4 返回空字符串 — 空字符串通过验证后返回")
        void l4EmptyResult() {
            // Empty string passes isValidTranslation (no ad keywords, length OK)
            // postProcessing returns it, shouldCache("Hello World", "") = true
            lenient().doReturn("{\"code\":200,\"data\":\"\"}").when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            // Empty string is valid per isValidTranslation (no ad keywords, 0 <= 11*10)
            assertEquals("", result);
        }

        @Test
        @DisplayName("译文与原文一致 — 跳过缓存")
        void shouldCacheSkippedWhenIdentical() {
            lenient().doReturn("{\"code\":200,\"data\":\"Hello World\"}").when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals("Hello World", result);
            verify(cacheService, never()).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("FAST 模式可读取 expert 和 team 层级缓存")
        void fastModeCacheHierarchy() {
            lenient().doReturn(Optional.of("cached result")).when(cacheService).getCacheByMode(anyString(), eq("fast"));

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals("cached result", result);
            verify(cacheService).getCacheByMode(anyString(), eq("fast"));
        }

        @Test
        @DisplayName("EXPERT 模式可读取 team 层级缓存")
        void expertModeCacheHierarchy() {
            lenient().doReturn(Optional.of("cached expert")).when(cacheService).getCacheByMode(anyString(), eq("expert"));

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals("cached expert", result);
        }
    }

    // ======================== execute() — Segmentation tests ========================

    @Nested
    @DisplayName("execute — 分段翻译")
    class SegmentationTests {

        @Test
        @DisplayName("短文本 — 不分段直接翻译")
        void shortTextNoSegmentation() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute("Hello", TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
        }

        @Test
        @DisplayName("长文本 — 按段落边界分段翻译")
        void longTextSegmentedByParagraphs() {
            // Build text with multiple paragraphs, total > 5000 chars to trigger segmentation
            String longParagraph = "This is a long paragraph. ".repeat(200); // ~5800 chars
            String longText = longParagraph + "\n\n" + "Another paragraph here.";

            lenient().doReturn("{\"code\":200,\"data\":\"翻译结果\"}").when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(longText, TARGET_LANG, TranslationMode.FAST);

            assertNotNull(result);
            // At least one segment was translated
            verify(translationClient, atLeastOnce()).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("分段中某段翻译失败 — 保留原文段")
        void segmentTranslationFailureKeepsOriginal() {
            // Build text that will be split into multiple segments (>5000 chars with \n\n boundary)
            String para1 = "First paragraph content that is long enough to overflow. ".repeat(110); // ~5720 chars
            String para2 = "Second paragraph that is short.";
            String longText = para1 + "\n\n" + para2;

            // Use an answer that returns null for the short segment (para2)
            lenient().doAnswer(inv -> {
                String text = inv.getArgument(0);
                if (text != null && text.length() > 1000) {
                    return "{\"code\":200,\"data\":\"翻译结果\"}";
                }
                return null;
            }).when(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(longText, TARGET_LANG, TranslationMode.FAST);

            assertNotNull(result);
            // The short segment (para2) failed translation, so original should be preserved
            assertTrue(result.contains(para2));
        }

        @Test
        @DisplayName("超长文本触发句子级别切分")
        void veryLongTextTriggersSentenceSplitting() {
            // Build a single massive paragraph (>5000 chars) that will overflow the 3000-char segment size
            // and exceed 4500 chars (3000 * 1.5) to trigger sentence-level splitting
            String sentence = "这是一个很长的句子。";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 600; i++) {
                sb.append(sentence);
            }
            String veryLongText = sb.toString();
            assertTrue(veryLongText.length() > 5000, "Text must exceed 5000 chars, was " + veryLongText.length());

            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(veryLongText, TARGET_LANG, TranslationMode.FAST);

            assertNotNull(result);
            // 600 sentences * 10 chars = 6000 chars total
            // splitAtSentenceBoundary produces segments of ~3000 chars each
            // So 6000 / 3000 = 2 segments minimum
            verify(translationClient, atLeast(2)).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }
    }

    // ======================== executeFast() tests ========================

    @Nested
    @DisplayName("executeFast — 快速模式管线")
    class ExecuteFastTests {

        @Test
        @DisplayName("缓存命中")
        void cacheHit() {
            lenient().doReturn(Optional.of("fast cached")).when(cacheService).getCacheByMode(anyString(), eq("fast"));

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals("fast cached", result);
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("缓存未命中 — 直译成功")
        void cacheMissDirectTranslationSuccess() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
        }

        @Test
        @DisplayName("HTML 模式翻译")
        void htmlMode() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), eq(true), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST, true);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), anyString(), eq(true), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("直译返回 null — 返回原文")
        void directTranslationReturnsNullReturnsOriginal() {
            lenient().doReturn(null).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
        }

        @Test
        @DisplayName("直译抛出异常 — 返回原文")
        void directTranslationThrowsReturnsOriginal() {
            lenient().doThrow(new RuntimeException("Connection refused")).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
        }

        @Test
        @DisplayName("翻译结果包含广告关键词 — 返回原文")
        void adKeywordsReturnsOriginal() {
            String adJson = "{\"code\":200,\"data\":\"体验生成式人工智能\"}";
            lenient().doReturn(adJson).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
        }

        @Test
        @DisplayName("译文与原文一致 — 跳过缓存")
        void identicalTextSkipsCache() {
            lenient().doReturn("{\"code\":200,\"data\":\"" + SOURCE_TEXT + "\"}").when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
            verify(cacheService, never()).putCache(anyString(), anyString(), eq(SOURCE_TEXT), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("术语表非空时强制走 Python（hasGlossary=true）")
        void glossaryForcesPythonService() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipelineWithGlossary.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            // useGlossary=false, fastMode=false because glossary is non-empty
            verify(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("直译结果为空字符串 — 返回原文")
        void blankResultReturnsOriginal() {
            lenient().doReturn("{\"code\":200,\"data\":\"  \"}").when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
        }
    }

    // ======================== executeTeam() tests ========================

    @Nested
    @DisplayName("executeTeam — 团队模式管线")
    class ExecuteTeamTests {

        @Test
        @DisplayName("L1 缓存命中 — 直接返回")
        void cacheHit() {
            lenient().doReturn(Optional.of("team cached")).when(cacheService).getCacheByMode(anyString(), eq("team"));

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "fantasy", List.of());

            assertEquals("team cached", result);
            verify(teamTranslationService, never()).translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("L2 RAG 直接命中")
        void ragHit() {
            RagTranslationResponse ragHit = buildRagHit("RAG团队翻译");
            lenient().doReturn(ragHit).when(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), anyList());

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "fantasy", List.of());

            assertEquals("RAG团队翻译", result);
            verify(teamTranslationService, never()).translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());
            verify(cacheService).putCache(anyString(), anyString(), eq("RAG团队翻译"), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("L3 实体一致性启用 — 占位符还原")
        void entityConsistencyWithPlaceholders() throws Exception {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(SOURCE_TEXT);
            lenient().doReturn(List.of("World")).when(entityConsistencyService).extractEntitiesSegmented(SOURCE_TEXT, TARGET_LANG);
            stubTranslateEntities(Map.of("World", "世界"));

            // Build a real EntityMappingContext
            com.yumu.noveltranslator.port.dto.translation.EntityMapping mapping = com.yumu.noveltranslator.port.dto.translation.EntityMapping.builder()
                    .sourceText("World").translatedText("世界").placeholder("__ENTITY_0__").index(0).build();
            var context = new EntityConsistencyService.EntityMappingContext(
                    List.of(mapping),
                    Map.of("World", "__ENTITY_0__"));

            lenient().doReturn(context).when(entityConsistencyService).buildMapping(anyMap());
            lenient().doAnswer(invocation -> {
                String t = invocation.getArgument(0, String.class);
                if (t.contains("World")) {
                    return t.replace("World", "__ENTITY_0__");
                }
                return t;
            }).when(entityConsistencyService).replaceEntitiesWithPlaceholders(anyString(), any(EntityConsistencyService.EntityMappingContext.class));
            lenient().doReturn("你好 __ENTITY_0__").when(teamTranslationService)
                    .translateChapter(anyString(), eq("fantasy"), eq("en"), eq(TARGET_LANG), anyList());
            lenient().doAnswer(invocation -> {
                String t = invocation.getArgument(0, String.class);
                if (t.contains("__ENTITY_0__")) {
                    return t.replace("__ENTITY_0__", "世界");
                }
                return t;
            }).when(entityConsistencyService).restorePlaceholders(anyString(), any(EntityConsistencyService.EntityMappingContext.class));

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "fantasy", List.of());

            assertEquals("你好 世界", result);
        }

        @Test
        @DisplayName("L3 实体一致性提取空列表 — 跳过占位符")
        void entityConsistencyEmptyExtractedEntities() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(SOURCE_TEXT);
            lenient().doReturn(List.of()).when(entityConsistencyService).extractEntitiesSegmented(SOURCE_TEXT, TARGET_LANG);

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals("Team translated result", result);
            verify(teamTranslationService).translateChapter(eq(SOURCE_TEXT), anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("L3 实体一致性抛出异常 — 降级为无占位符翻译")
        void entityConsistencyExceptionFallsBack() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(SOURCE_TEXT);
            lenient().doThrow(new RuntimeException("Entity extraction failed"))
                    .when(entityConsistencyService).extractEntitiesSegmented(SOURCE_TEXT, TARGET_LANG);

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals("Team translated result", result);
            verify(teamTranslationService).translateChapter(eq(SOURCE_TEXT), anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("L4 teamTranslationService 为 null — 降级到 executeSegment")
        void teamServiceNullFallsBackToStandardPipeline() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("team"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals(TRANSLATED_TEXT, result);
            verify(teamTranslationService, never()).translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("L4 teamTranslationService 返回 null — 整体返回 null")
        void teamServiceReturnsNull() {
            lenient().doReturn(null).when(teamTranslationService)
                    .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertNull(result);
        }

        @Test
        @DisplayName("L4 teamTranslationService 返回空字符串 — 整体返回 null")
        void teamServiceReturnsEmptyString() {
            lenient().doReturn("").when(teamTranslationService)
                    .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertNull(result);
        }

        @Test
        @DisplayName("L4 teamTranslationService 返回空白字符串 — 整体返回 null")
        void teamServiceReturnsBlankString() {
            lenient().doReturn("   ").when(teamTranslationService)
                    .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertNull(result);
        }

        @Test
        @DisplayName("L4 teamTranslationService 抛出异常 — 返回 null")
        void teamServiceThrowsException() {
            lenient().doThrow(new RuntimeException("Team service error")).when(teamTranslationService)
                    .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertNull(result);
        }

        @Test
        @DisplayName("正常流程 — 后处理 + 缓存 + RAG 存储")
        void normalFlowWithPostProcessCacheAndRag() {
            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals("Team translated result", result);
            verify(postProcessingService).fixUntranslatedChinese(eq(SOURCE_TEXT), eq("Team translated result"), eq(TARGET_LANG), eq("team"));
            verify(cacheService).putCache(anyString(), anyString(), eq("Team translated result"), anyString(), anyString(), anyString(), anyString());
            verify(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("团队模式 + 术语表注入")
        void teamModeWithGlossary() {
            Glossary g = new Glossary();
            g.setSourceWord("magic");
            g.setTargetWord("魔法");
            List<Glossary> glossaryList = List.of(g);

            lenient().doReturn("魔法世界").when(teamTranslationService)
                    .translateChapter(anyString(), eq("fantasy"), eq("en"), eq(TARGET_LANG), eq(glossaryList));

            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "fantasy", glossaryList);

            assertEquals("魔法世界", result);
            verify(teamTranslationService).translateChapter(anyString(), eq("fantasy"), eq("en"), eq(TARGET_LANG), eq(glossaryList));
        }
    }

    // ======================== Glossary term injection tests ========================

    @Nested
    @DisplayName("术语表注入")
    class GlossaryTests {

        @Test
        @DisplayName("无术语表 — 不带术语表参数")
        void noGlossary() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }

        @Test
        @DisplayName("有术语表 — glossaryTerms 传入")
        void withGlossary() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), eq(false), eq(true), anyList(), any(), any());

            String result = pipelineWithGlossary.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(eq(SOURCE_TEXT), eq(TARGET_LANG), eq("fast"), eq(false), eq(true), argThat(terms -> terms != null && terms.size() == 1), any(), any());
        }

        @Test
        @DisplayName("快速模式有术语表 — 强制 Python 服务")
        void fastModeWithGlossaryForcesPython() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipelineWithGlossary.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(eq(SOURCE_TEXT), eq(TARGET_LANG), eq("fast"), anyBoolean(), anyBoolean(), argThat(terms -> terms != null && terms.size() == 1), any(), any());
        }

        @Test
        @DisplayName("executeSegment 有术语表 — L4 调用携带术语表")
        void executeSegmentWithGlossary() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("expert"), eq(false), eq(false), anyList(), any(), any());

            String result = pipelineWithGlossary.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(eq(SOURCE_TEXT), eq(TARGET_LANG), eq("expert"), eq(false), eq(false), argThat(terms -> terms != null && !terms.isEmpty()), any(), any());
        }
    }

    // ======================== Error handling tests ========================

    @Nested
    @DisplayName("错误处理")
    class ErrorHandlingTests {

        @Test
        @DisplayName("RAG 服务抛出异常 — 传播到调用方")
        void ragServiceExceptionPropagates() {
            lenient().doThrow(new RuntimeException("RAG service error"))
                    .when(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), anyList());

            // executeSegment does NOT wrap the RAG call in try-catch
            assertThrows(RuntimeException.class, () ->
                    pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST));
        }

        @Test
        @DisplayName("缓存服务抛出异常")
        void cacheServiceException() {
            lenient().doThrow(new RuntimeException("Cache error"))
                    .when(cacheService).getCacheByMode(anyString(), anyString());

            assertThrows(RuntimeException.class, () ->
                    pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST));
        }

        @Test
        @DisplayName("翻译客户端抛出异常 — executeFast 捕获返回原文")
        void translationClientExceptionInFastMode() {
            lenient().doThrow(new RuntimeException("Network error")).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeFast(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(SOURCE_TEXT, result);
        }

        @Test
        @DisplayName("后处理服务抛出异常")
        void postProcessingException() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
            lenient().doThrow(new RuntimeException("Post-processing error"))
                    .when(postProcessingService).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());

            assertThrows(RuntimeException.class, () ->
                    pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST));
        }

        @Test
        @DisplayName("团队模式 — translationClient 为 null 且 teamService 也为 null")
        void teamModeBothClientsNull() {
            TranslationPipeline barePipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    null, postProcessingService,
                    null, USER_ID, null, DOC_ID);

            String result = barePipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertNull(result);
        }
    }

    // ======================== Empty/null input handling ========================

    @Nested
    @DisplayName("空值/边界输入处理")
    class EmptyInputTests {

        @Test
        @DisplayName("null 文本 — 分段为空字符串，翻译长度校验失败返回 null")
        void nullTextHandled() {
            // Create fresh mocks to avoid interference from setUp stubs
            TranslationCachePort cs = mock(TranslationCachePort.class);
            RagTranslationApplicationService rag = mock(RagTranslationApplicationService.class);
            EntityConsistencyService ecs = mock(EntityConsistencyService.class);
            TranslationPostProcessingService pps = mock(TranslationPostProcessingService.class);
            UserLevelThrottledTranslationClient tc = mock(UserLevelThrottledTranslationClient.class);

            lenient().doReturn(Optional.empty()).when(cs).getCacheByMode(anyString(), anyString());
            lenient().doNothing().when(cs).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            lenient().doReturn(buildRagMiss()).when(rag).searchSimilarWithModes(anyLong(), any(), any(), any());
            lenient().doNothing().when(rag).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
            lenient().doReturn(false).when(ecs).shouldUseConsistency(anyString());
            lenient().doReturn("{\"code\":200,\"data\":\"翻译\"}").when(tc)
                    .translate(any(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), any());
            lenient().doAnswer(inv -> inv.getArgument(1)).when(pps).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());

            TranslationPipeline p = new TranslationPipeline(cs, rag, ecs, tc, pps, USER_ID, null, DOC_ID);

            // null → splitTextForTranslation returns List.of("") → executeSegment("")
            // isValidTranslation("", "翻译") fails because 2 > 0*10
            String result = p.execute(null, TARGET_LANG, TranslationMode.FAST);

            assertNull(result);
        }

        @Test
        @DisplayName("空字符串文本 — 翻译长度校验失败返回 null")
        void emptyText() {
            TranslationCachePort cs = mock(TranslationCachePort.class);
            RagTranslationApplicationService rag = mock(RagTranslationApplicationService.class);
            EntityConsistencyService ecs = mock(EntityConsistencyService.class);
            TranslationPostProcessingService pps = mock(TranslationPostProcessingService.class);
            UserLevelThrottledTranslationClient tc = mock(UserLevelThrottledTranslationClient.class);

            lenient().doReturn(Optional.empty()).when(cs).getCacheByMode(anyString(), anyString());
            lenient().doNothing().when(cs).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            lenient().doReturn(buildRagMiss()).when(rag).searchSimilarWithModes(anyLong(), any(), any(), any());
            lenient().doNothing().when(rag).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
            lenient().doReturn(false).when(ecs).shouldUseConsistency(anyString());
            lenient().doReturn("{\"code\":200,\"data\":\"空翻译\"}").when(tc)
                    .translate(any(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), any());
            lenient().doAnswer(inv -> inv.getArgument(1)).when(pps).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());

            TranslationPipeline p = new TranslationPipeline(cs, rag, ecs, tc, pps, USER_ID, null, DOC_ID);

            // isValidTranslation("", "空翻译") fails because 5 > 0*10
            String result = p.execute("", TARGET_LANG, TranslationMode.FAST);

            assertNull(result);
        }

        @Test
        @DisplayName("空白字符串文本")
        void blankText() {
            TranslationCachePort cs = mock(TranslationCachePort.class);
            RagTranslationApplicationService rag = mock(RagTranslationApplicationService.class);
            EntityConsistencyService ecs = mock(EntityConsistencyService.class);
            TranslationPostProcessingService pps = mock(TranslationPostProcessingService.class);
            UserLevelThrottledTranslationClient tc = mock(UserLevelThrottledTranslationClient.class);

            lenient().doReturn(Optional.empty()).when(cs).getCacheByMode(anyString(), anyString());
            lenient().doNothing().when(cs).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
            lenient().doReturn(buildRagMiss()).when(rag).searchSimilarWithModes(anyLong(), any(), any(), any());
            lenient().doNothing().when(rag).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
            lenient().doReturn(false).when(ecs).shouldUseConsistency(anyString());
            lenient().doReturn("{\"code\":200,\"data\":\"空白翻译\"}").when(tc)
                    .translate(any(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), any());
            lenient().doAnswer(inv -> inv.getArgument(1)).when(pps).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());

            TranslationPipeline p = new TranslationPipeline(cs, rag, ecs, tc, pps, USER_ID, null, DOC_ID);

            String result = p.execute("   ", TARGET_LANG, TranslationMode.FAST);

            assertEquals("空白翻译", result);
        }

        @Test
        @DisplayName("快速模式 — null 文本抛出 NPE")
        void fastModeNullText() {
            // executeFast 在日志中调用 text.length() 不对 null 做防护
            TranslationCachePort cs = mock(TranslationCachePort.class);
            RagTranslationApplicationService rag = mock(RagTranslationApplicationService.class);
            EntityConsistencyService ecs = mock(EntityConsistencyService.class);
            TranslationPostProcessingService pps = mock(TranslationPostProcessingService.class);
            UserLevelThrottledTranslationClient tc = mock(UserLevelThrottledTranslationClient.class);

            lenient().doReturn(Optional.empty()).when(cs).getCacheByMode(anyString(), anyString());

            TranslationPipeline p = new TranslationPipeline(cs, rag, ecs, tc, pps, USER_ID, null, DOC_ID);

            assertThrows(NullPointerException.class, () -> p.executeFast(null, TARGET_LANG, TranslationMode.FAST));
        }

        @Test
        @DisplayName("executeFast — 空字符串返回原文")
        void fastModeEmptyText() {
            TranslationCachePort cs = mock(TranslationCachePort.class);
            RagTranslationApplicationService rag = mock(RagTranslationApplicationService.class);
            EntityConsistencyService ecs = mock(EntityConsistencyService.class);
            TranslationPostProcessingService pps = mock(TranslationPostProcessingService.class);
            UserLevelThrottledTranslationClient tc = mock(UserLevelThrottledTranslationClient.class);

            lenient().doReturn(Optional.empty()).when(cs).getCacheByMode(anyString(), anyString());
            lenient().doReturn(null).when(tc)
                    .translate(any(), anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any(), any());

            TranslationPipeline p = new TranslationPipeline(cs, rag, ecs, tc, pps, USER_ID, null, DOC_ID);

            String result = p.executeFast("", TARGET_LANG, TranslationMode.FAST);

            assertEquals("", result);
        }
    }

    // ======================== shouldCache static method tests ========================

    @Nested
    @DisplayName("shouldCache — 静态方法")
    class ShouldCacheTests {

        @Test
        @DisplayName("原文为 null — 返回 false")
        void originalNull() {
            assertFalse(TranslationPipeline.shouldCache(null, "translated"));
        }

        @Test
        @DisplayName("译文为 null — 返回 false")
        void translatedNull() {
            assertFalse(TranslationPipeline.shouldCache("original", null));
        }

        @Test
        @DisplayName("原文和译文相同 — 返回 false")
        void identical() {
            assertFalse(TranslationPipeline.shouldCache("hello", "hello"));
        }

        @Test
        @DisplayName("原文和译文忽略大小写相同 — 返回 false")
        void identicalIgnoreCase() {
            assertFalse(TranslationPipeline.shouldCache("Hello", "hello"));
        }

        @Test
        @DisplayName("原文和译文有前导/尾随空白但内容相同 — 返回 false")
        void identicalWithWhitespace() {
            assertFalse(TranslationPipeline.shouldCache(" hello ", "hello"));
        }

        @Test
        @DisplayName("原文和译文不同 — 返回 true")
        void different() {
            assertTrue(TranslationPipeline.shouldCache("Hello", "你好"));
        }
    }

    // ======================== isValidTranslation static method tests ========================

    @Nested
    @DisplayName("isValidTranslation — 静态方法")
    class IsValidTranslationTests {

        @Test
        @DisplayName("原文为 null — 返回 false")
        void textNull() {
            assertFalse(TranslationPipeline.isValidTranslation(null, "result"));
        }

        @Test
        @DisplayName("结果为 null — 返回 false")
        void resultNull() {
            assertFalse(TranslationPipeline.isValidTranslation("text", null));
        }

        @Test
        @DisplayName("包含广告关键词 — 返回 false")
        void adKeyword() {
            assertFalse(TranslationPipeline.isValidTranslation("text", "人工智能助手"));
            assertFalse(TranslationPipeline.isValidTranslation("text", "生成式人工智能"));
            assertFalse(TranslationPipeline.isValidTranslation("text", "体验生成式"));
            assertFalse(TranslationPipeline.isValidTranslation("text", "获取写作"));
            assertFalse(TranslationPipeline.isValidTranslation("text", "Gemini"));
            assertFalse(TranslationPipeline.isValidTranslation("text", "Google AI"));
        }

        @Test
        @DisplayName("译文长度超过原文 10 倍 — 返回 false")
        void abnormalLength() {
            String text = "abc";
            String result = "x".repeat(text.length() * 10 + 1);
            assertFalse(TranslationPipeline.isValidTranslation(text, result));
        }

        @Test
        @DisplayName("译文长度等于原文 10 倍 — 返回 true（边界值）")
        void exactTenTimesLength() {
            String text = "abc";
            String result = "x".repeat(text.length() * 10);
            assertTrue(TranslationPipeline.isValidTranslation(text, result));
        }

        @Test
        @DisplayName("正常翻译 — 返回 true")
        void validTranslation() {
            assertTrue(TranslationPipeline.isValidTranslation("Hello World", "你好世界"));
        }
    }

    // ======================== TranslationMode coverage ========================

    @Nested
    @DisplayName("翻译模式覆盖")
    class TranslationModeTests {

        @Test
        @DisplayName("FAST 模式 — useGlossary=true, 可读取所有层级缓存")
        void fastMode() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            verify(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
            verify(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), eq(List.of("team", "expert", "fast")));
        }

        @Test
        @DisplayName("EXPERT 模式 — useGlossary=true, 可读取 team/expert 缓存")
        void expertMode() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            verify(translationClient).translate(anyString(), anyString(), eq("expert"), anyBoolean(), anyBoolean(), anyList(), any(), any());
            verify(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), eq(List.of("team", "expert")));
        }

        @Test
        @DisplayName("TEAM 模式通过 executeTeam — 仅读取 team 缓存")
        void teamModeViaExecuteTeam() {
            teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            verify(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), eq(List.of("team")));
        }

        @Test
        @DisplayName("TEAM 模式 — 无 teamService 时降级到 executeSegment")
        void teamModeFallsBackWhenNoTeamService() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("team"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals(TRANSLATED_TEXT, result);
            verify(translationClient).translate(anyString(), anyString(), eq("team"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }
    }

    // ======================== Constructor tests ========================

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("三参数构造 — teamService=null, glossaryTerms=empty list")
        void threeArgConstructor() {
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    USER_ID, null, DOC_ID);

            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = p.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);
            assertEquals(TRANSLATED_TEXT, result);
        }

        @Test
        @DisplayName("六参数构造（含 teamService）— glossaryTerms=empty list")
        void sixArgConstructor() {
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    teamTranslationService, USER_ID, null, DOC_ID);

            String result = p.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());
            assertEquals("Team translated result", result);
        }

        @Test
        @DisplayName("完整构造 — glossaryTerms 传入非 null 列表")
        void fullConstructorWithGlossary() {
            Glossary g = new Glossary();
            g.setSourceWord("hero");
            g.setTargetWord("英雄");
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    teamTranslationService, USER_ID, null, DOC_ID, List.of(g));

            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = p.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);
            assertEquals(TRANSLATED_TEXT, result);
        }

        @Test
        @DisplayName("完整构造 — glossaryTerms 传入 null，转换为空列表")
        void fullConstructorNullGlossary() {
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    null, USER_ID, null, DOC_ID, null);

            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = p.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);
            assertEquals(TRANSLATED_TEXT, result);
            // null glossary -> converted to empty list -> !isEmpty() = false
            verify(translationClient).translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());
        }
    }

    // ======================== Post-processing and caching integration ========================

    @Nested
    @DisplayName("后处理与缓存集成")
    class PostProcessingAndCachingTests {

        @Test
        @DisplayName("RAG 命中 — 后处理 + 缓存（不调用 storeTranslationMemory）")
        void ragHitPostProcessAndCache() {
            RagTranslationResponse ragHit = buildRagHit("RAG翻译结果");
            lenient().doReturn(ragHit).when(ragTranslationService).searchSimilarWithModes(anyLong(), anyString(), anyString(), anyList());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals("RAG翻译结果", result);
            verify(postProcessingService).fixUntranslatedChinese(eq(SOURCE_TEXT), eq("RAG翻译结果"), eq(TARGET_LANG), eq("fast"));
            verify(cacheService).putCache(anyString(), anyString(), eq("RAG翻译结果"), eq("auto"), eq(TARGET_LANG), eq("fast"), eq("fast"));
            // RAG hit path does NOT call storeTranslationMemory (only L4 and entity consistency do)
            verify(ragTranslationService, never()).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("实体一致性命中 — 后处理 + 条件缓存 + RAG 存储")
        void entityConsistencyPostProcessCacheAndRag() {
            lenient().doReturn(true).when(entityConsistencyService).shouldUseConsistency(anyString());
            lenient().doReturn(buildConsistencyResult("一致性结果", true)).when(entityConsistencyService)
                    .translateWithConsistency(anyString(), anyString(), anyString(), anyLong(), anyString());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.EXPERT);

            assertEquals("一致性结果", result);
            verify(postProcessingService).fixUntranslatedChinese(eq(SOURCE_TEXT), eq("一致性结果"), eq(TARGET_LANG), eq("expert"));
            verify(cacheService).putCache(anyString(), anyString(), eq("一致性结果"), eq("auto"), eq(TARGET_LANG), eq("expert"), eq("expert"));
            verify(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("L4 直译成功 — 后处理 + 条件缓存 + RAG 存储")
        void l4PostProcessCacheAndRag() {
            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = pipeline.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(postProcessingService).fixUntranslatedChinese(eq(SOURCE_TEXT), eq(TRANSLATED_TEXT), eq(TARGET_LANG), eq("fast"));
            verify(cacheService).putCache(anyString(), anyString(), eq(TRANSLATED_TEXT), eq("auto"), eq(TARGET_LANG), eq("fast"), eq("fast"));
            verify(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("团队模式 — 后处理 + 缓存 + RAG 存储（含 mode 参数）")
        void teamModePostProcessCacheAndRag() {
            String result = teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals("Team translated result", result);
            verify(cacheService).putCache(anyString(), anyString(), eq("Team translated result"), eq("en"), eq(TARGET_LANG), eq("team"), eq("team"));
            verify(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("团队模式译文与原文一致 — 跳过缓存")
        void teamModeIdenticalSkipsCache() {
            lenient().doReturn(SOURCE_TEXT).when(teamTranslationService)
                    .translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());

            teamPipeline.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            verify(cacheService, never()).putCache(anyString(), anyString(), eq(SOURCE_TEXT), anyString(), anyString(), anyString(), anyString());
            verify(ragTranslationService, never()).storeTranslationMemory(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }
    }

    // ======================== userId null handling ========================

    @Nested
    @DisplayName("userId 为 null 的处理")
    class NullUserIdTests {

        @Test
        @DisplayName("userId 为 null — executeSegment 跳过实体一致性")
        void nullUserIdSkipsEntityConsistency() {
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    null, null, DOC_ID);

            lenient().doReturn(SUCCESS_JSON).when(translationClient)
                    .translate(anyString(), anyString(), eq("fast"), anyBoolean(), anyBoolean(), anyList(), any(), any());

            String result = p.execute(SOURCE_TEXT, TARGET_LANG, TranslationMode.FAST);

            assertEquals(TRANSLATED_TEXT, result);
            verify(entityConsistencyService, never()).shouldUseConsistency(anyString());
        }

        @Test
        @DisplayName("userId 为 null — executeTeam 跳过实体一致性")
        void nullUserIdSkipsEntityConsistencyInTeam() {
            TranslationPipeline p = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    translationClient, postProcessingService,
                    teamTranslationService, null, null, DOC_ID);

            String result = p.executeTeam(SOURCE_TEXT, "en", TARGET_LANG, TranslationMode.TEAM, "daily", List.of());

            assertEquals("Team translated result", result);
            verify(entityConsistencyService, never()).shouldUseConsistency(anyString());
        }
    }
}
