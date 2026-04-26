package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.*;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MultiAgentTranslationService 补充测试")
class MultiAgentTranslationServiceExtendedTest {

    @Mock
    private CollabChapterTaskMapper chapterTaskMapper;
    @Mock
    private CollabProjectMapper collabProjectMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private TranslationTaskMapper translationTaskMapper;
    @Mock
    private TeamTranslationService teamTranslationService;
    @Mock
    private TranslationCacheService cacheService;
    @Mock
    private EntityConsistencyService entityConsistencyService;
    @Mock
    private GlossaryMapper glossaryMapper;
    @Mock
    private RagTranslationService ragTranslationService;
    @Mock
    private AiGlossaryService aiGlossaryService;
    @Mock
    private TranslationPostProcessingService postProcessingService;

    private MultiAgentTranslationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MultiAgentTranslationService(
                chapterTaskMapper, collabProjectMapper, documentMapper, translationTaskMapper,
                teamTranslationService, cacheService, entityConsistencyService,
                glossaryMapper, ragTranslationService, aiGlossaryService, postProcessingService);
        clearRetryCounterMap();
        // postProcessingService fixUntranslatedChinese 默认返回译文本身
        lenient().doAnswer(inv -> inv.getArgument(1)).when(postProcessingService).fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString());
        // cache stubs
        lenient().doReturn(null).when(cacheService).getCache(anyString());
        lenient().doNothing().when(cacheService).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        lenient().doNothing().when(cacheService).putCache(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        // RAG stubs
        lenient().doReturn(new RagTranslationResponse()).when(ragTranslationService).searchSimilar(anyString(), anyString(), anyString());
        lenient().doNothing().when(ragTranslationService).storeTranslationMemory(anyString(), anyString(), anyString(), anyString());
        // Team translation stub
        lenient().doReturn("AI翻译结果").when(teamTranslationService).translateChapter(anyString(), anyString(), anyString(), anyString(), anyList());
        // AI glossary stub
        lenient().doReturn(List.of()).when(aiGlossaryService).getProjectGlossary(anyLong());
        lenient().doNothing().when(aiGlossaryService).addTerm(anyLong(), anyString(), anyString(), anyString(), any(), any());
        // Entity consistency stub
        lenient().doReturn(false).when(entityConsistencyService).shouldUseConsistency(anyString());
        clearRetryCounterMap();
    }

    private void clearRetryCounterMap() throws Exception {
        java.lang.reflect.Field field = MultiAgentTranslationService.class.getDeclaredField("retryCounterMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Long, Integer> map = (java.util.Map<Long, Integer>) field.get(null);
        map.clear();
    }

    // ============ deriveNovelType 测试 ============

    @Nested
    @DisplayName("deriveNovelType - 小说类型推导")
    class DeriveNovelTypeTests {

        @Test
        void 项目为空返回daily() throws Exception {
            String result = invokeDeriveNovelType(null);
            assertEquals("daily", result);
        }

        @Test
        void 描述为空返回daily() throws Exception {
            CollabProject project = new CollabProject();
            project.setDescription("");
            assertEquals("daily", invokeDeriveNovelType(project));
        }

        @Test
        void 描述为null返回daily() throws Exception {
            CollabProject project = new CollabProject();
            project.setDescription(null);
            assertEquals("daily", invokeDeriveNovelType(project));
        }

        @Test
        void 包含fantasy关键词返回fantasy() throws Exception {
            assertEquals("fantasy", invokeDeriveNovelType(desc("这是一个奇幻 fantasy 故事")));
            assertEquals("fantasy", invokeDeriveNovelType(desc("玄幻小说")));
            assertEquals("fantasy", invokeDeriveNovelType(desc("FANTASY adventure")));
        }

        @Test
        void 包含romance关键词返回romance() throws Exception {
            assertEquals("romance", invokeDeriveNovelType(desc("这是一个 romance 爱情故事")));
            assertEquals("romance", invokeDeriveNovelType(desc("言情小说")));
            assertEquals("romance", invokeDeriveNovelType(desc("恋爱故事")));
        }

        @Test
        void 包含scifi关键词返回scifi() throws Exception {
            assertEquals("scifi", invokeDeriveNovelType(desc("科幻 scifi 小说")));
            assertEquals("scifi", invokeDeriveNovelType(desc("SCIFI adventure")));
        }

        @Test
        void 包含mystery关键词返回mystery() throws Exception {
            assertEquals("mystery", invokeDeriveNovelType(desc("mystery 推理小说")));
            assertEquals("mystery", invokeDeriveNovelType(desc("悬疑故事")));
            assertEquals("mystery", invokeDeriveNovelType(desc("推理小说")));
        }

        @Test
        void 包含horror关键词返回horror() throws Exception {
            assertEquals("horror", invokeDeriveNovelType(desc("恐怖 horror 小说")));
        }

        @Test
        void 包含daily关键词返回daily() throws Exception {
            assertEquals("daily", invokeDeriveNovelType(desc("日常生活 小说")));
        }

        @Test
        void 未知关键词返回daily() throws Exception {
            assertEquals("daily", invokeDeriveNovelType(desc("这是一本关于烹饪的书")));
        }

        private CollabProject desc(String d) {
            CollabProject p = new CollabProject();
            p.setDescription(d);
            return p;
        }

        private String invokeDeriveNovelType(CollabProject project) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("deriveNovelType", CollabProject.class);
            m.setAccessible(true);
            return (String) m.invoke(service, project);
        }
    }

    // ============ buildBaseCacheKey 测试 ============

    @Nested
    @DisplayName("buildBaseCacheKey - 缓存键构建")
    class BuildCacheKeyTests {

        @Test
        void 正常构建SHA256缓存键() throws Exception {
            String key = invokeBuildBaseCacheKey("Hello World", "zh");
            assertNotNull(key);
            assertFalse(key.isEmpty());
            assertTrue(key.endsWith("_zh"));
        }

        @Test
        void 相同文本生成相同缓存键() throws Exception {
            String key1 = invokeBuildBaseCacheKey("Hello", "en");
            String key2 = invokeBuildBaseCacheKey("Hello", "en");
            assertEquals(key1, key2);
        }

        @Test
        void 不同文本生成不同缓存键() throws Exception {
            String key1 = invokeBuildBaseCacheKey("Hello", "zh");
            String key2 = invokeBuildBaseCacheKey("World", "zh");
            assertNotEquals(key1, key2);
        }

        @Test
        void 不同目标语言缓存键不同() throws Exception {
            String key1 = invokeBuildBaseCacheKey("Hello", "zh");
            String key2 = invokeBuildBaseCacheKey("Hello", "en");
            assertNotEquals(key1, key2);
        }

        private String invokeBuildBaseCacheKey(String text, String target) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("buildBaseCacheKey", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(service, text, target);
        }
    }

    // ============ translateChapter - 基本流程测试 ============

    @Nested
    @DisplayName("translateChapter - 基本流程")
    class TranslateChapterTests {

        @Test
        void 章节内容为空返回false() throws Exception {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(1L);
            chapter.setProjectId(1L);
            chapter.setSourceText("   ");
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setChapterNumber(1);

            boolean result = invokeTranslateChapter(chapter, createProject());
            assertFalse(result);
        }

        @Test
        void 缓存命中通过管线翻译成功() throws Exception {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(1L);
            chapter.setProjectId(1L);
            chapter.setSourceText("Hello World");
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setChapterNumber(1);
            CollabProject project = createProject();

            boolean result = invokeTranslateChapter(chapter, project);
            assertTrue(result);
            // 翻译管线成功执行后标记为 SUBMITTED（待审校）
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), chapter.getStatus());
            assertEquals(80, chapter.getProgress());
            assertNotNull(chapter.getTargetText());
        }

        @Test
        void 正常翻译流程成功() throws Exception {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(1L);
            chapter.setProjectId(1L);
            chapter.setSourceText("Hello World");
            chapter.setAssigneeId(1L);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setChapterNumber(1);
            CollabProject project = createProject();

            boolean result = invokeTranslateChapter(chapter, project);
            assertTrue(result);
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), chapter.getStatus());
            assertEquals(80, chapter.getProgress());
            assertNotNull(chapter.getTargetText());
        }

        private CollabProject createProject() {
            CollabProject p = new CollabProject();
            p.setId(1L);
            p.setOwnerId(1L);
            p.setSourceLang("en");
            p.setTargetLang("zh");
            p.setDescription("daily novel");
            p.setDocumentId(10L);
            return p;
        }

        private boolean invokeTranslateChapter(CollabChapterTask chapter, CollabProject project) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("translateChapter", CollabChapterTask.class, CollabProject.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(service, chapter, project);
        }

        private void invokeSetRetryCount(CollabChapterTask chapter, int count) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("incrementRetryCount", CollabChapterTask.class);
            m.setAccessible(true);
            for (int i = 0; i < count; i++) {
                m.invoke(service, chapter);
            }
        }
    }

    // ============ recoverStuckChapters 测试 ============

    @Nested
    @DisplayName("recoverStuckChapters - 恢复中断翻译")
    class RecoverStuckChaptersTests {

        @Test
        void 没有中断章节直接返回() throws Exception {
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), eq("TRANSLATING")))
                    .thenReturn(List.of());

            invokeRecoverStuckChapters(1L);

            verify(chapterTaskMapper, never()).updateById(any());
        }

        @Test
        void 中断章节回退到UNASSIGNED() throws Exception {
            CollabChapterTask stuck = new CollabChapterTask();
            stuck.setId(1L);
            stuck.setProjectId(1L);
            stuck.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), eq("TRANSLATING")))
                    .thenReturn(List.of(stuck));

            invokeRecoverStuckChapters(1L);

            assertEquals(ChapterTaskStatus.UNASSIGNED.getValue(), stuck.getStatus());
            assertTrue(stuck.getReviewComment().contains("中断"));
        }

        @Test
        void 重试次数超限则跳过() throws Exception {
            CollabChapterTask stuck = new CollabChapterTask();
            stuck.setId(1L);
            stuck.setProjectId(1L);
            stuck.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), eq("TRANSLATING")))
                    .thenReturn(List.of(stuck));

            // Pre-set retry count to 3
            Method inc = MultiAgentTranslationService.class.getDeclaredMethod("incrementRetryCount", CollabChapterTask.class);
            inc.setAccessible(true);
            for (int i = 0; i < 3; i++) {
                inc.invoke(service, stuck);
            }

            invokeRecoverStuckChapters(1L);

            // Should NOT be reset to UNASSIGNED
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), stuck.getStatus());
        }

        private void invokeRecoverStuckChapters(Long projectId) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("recoverStuckChapters", Long.class);
            m.setAccessible(true);
            m.invoke(service, projectId);
        }
    }

    // ============ applyTranslationResult 测试 ============

    @Nested
    @DisplayName("applyTranslationResult - 应用翻译结果")
    class ApplyTranslationResultTests {

        @Test
        void 缓存命中标记COMPLETED() throws Exception {
            CollabChapterTask chapter = createChapter();
            invokeApplyTranslationResult(chapter, "原文", "译文", true);

            assertEquals("译文", chapter.getTargetText());
            assertEquals(2, chapter.getTargetWordCount());
            assertEquals(2, chapter.getSourceWordCount());
            assertEquals(100, chapter.getProgress());
            assertEquals(ChapterTaskStatus.COMPLETED.getValue(), chapter.getStatus());
            assertNotNull(chapter.getCompletedTime());
        }

        @Test
        void 新翻译标记为SUBMITTED() throws Exception {
            CollabChapterTask chapter = createChapter();
            invokeApplyTranslationResult(chapter, "原文", "译文", false);

            assertEquals(80, chapter.getProgress());
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), chapter.getStatus());
            assertNotNull(chapter.getSubmittedTime());
        }

        private CollabChapterTask createChapter() {
            CollabChapterTask c = new CollabChapterTask();
            c.setId(1L);
            c.setProjectId(1L);
            return c;
        }

        private void invokeApplyTranslationResult(CollabChapterTask chapter, String source, String translated, boolean fromCache) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("applyTranslationResult",
                    CollabChapterTask.class, String.class, String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(service, chapter, source, translated, fromCache);
        }
    }

    // ============ loadGlossaryTermsForProject 测试 ============

    @Nested
    @DisplayName("loadGlossaryTermsForProject - 加载术语表")
    class LoadGlossaryTests {

        @Test
        void userId为空返回空列表() throws Exception {
            List<?> result = invokeLoadGlossaryTerms(null, "some text");
            assertTrue(result.isEmpty());
        }

        @Test
        void 没有术语表返回空列表() throws Exception {
            when(glossaryMapper.selectList(any())).thenReturn(List.of());
            List<?> result = invokeLoadGlossaryTerms(1L, "Hello World");
            assertTrue(result.isEmpty());
        }

        @Test
        void 只返回在原文中出现的术语() throws Exception {
            Glossary g1 = new Glossary();
            g1.setSourceWord("Hello");
            g1.setTargetWord("你好");

            Glossary g2 = new Glossary();
            g2.setSourceWord("Goodbye");
            g2.setTargetWord("再见");

            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1, g2));

            List<?> result = invokeLoadGlossaryTerms(1L, "Hello World");

            assertEquals(1, result.size());
        }

        @Test
        void sourceWord为null的术语被过滤() throws Exception {
            Glossary g1 = new Glossary();
            g1.setSourceWord(null);
            g1.setTargetWord("你好");

            Glossary g2 = new Glossary();
            g2.setSourceWord("Hello");
            g2.setTargetWord("你好");

            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1, g2));

            List<?> result = invokeLoadGlossaryTerms(1L, "Hello World");

            assertEquals(1, result.size());
        }

        @Test
        void 查询异常返回空列表() throws Exception {
            when(glossaryMapper.selectList(any())).thenThrow(new RuntimeException("DB error"));
            List<?> result = invokeLoadGlossaryTerms(1L, "Hello");
            assertTrue(result.isEmpty());
        }

        private List<?> invokeLoadGlossaryTerms(Long userId, String sourceText) throws Exception {
            Method m = MultiAgentTranslationService.class.getDeclaredMethod("loadGlossaryTermsForProject", Long.class, String.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(service, userId, sourceText);
        }
    }
}
