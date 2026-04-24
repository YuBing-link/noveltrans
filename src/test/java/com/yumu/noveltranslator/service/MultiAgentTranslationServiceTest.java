package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.TranslationTask;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiAgentTranslationServiceTest {

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

    private MultiAgentTranslationService service;

    @BeforeEach
    void setUp() {
        service = new MultiAgentTranslationService(
                chapterTaskMapper, collabProjectMapper, documentMapper, translationTaskMapper,
                teamTranslationService, cacheService, entityConsistencyService,
                glossaryMapper, ragTranslationService, aiGlossaryService);
    }

    @Nested
    @DisplayName("startMultiAgentTranslation - 前置校验")
    class StartValidationTests {

        @Test
        void 没有待翻译章节直接返回() {
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), anyString()))
                    .thenReturn(Collections.emptyList());

            service.startMultiAgentTranslation(1L);

            verify(collabProjectMapper, never()).selectById(anyLong());
            verify(chapterTaskMapper, atLeastOnce()).selectByProjectIdAndStatus(anyLong(), anyString());
        }

        @Test
        void 项目不存在记录错误并返回() {
            List<CollabChapterTask> chapters = new ArrayList<>();
            chapters.add(createChapter(1L, 1L, "Some text"));
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), anyString()))
                    .thenReturn(chapters);
            when(collabProjectMapper.selectById(1L)).thenReturn(null);

            // Since virtual threads are involved, we just verify the mapper calls
            service.startMultiAgentTranslation(1L);

            verify(collabProjectMapper).selectById(1L);
        }
    }

    @Nested
    @DisplayName("assembleCompleteDocument - 文档拼接")
    class AssembleDocumentTests {

        private Path tempDir;
        private String originalPath;

        @BeforeEach
        void setUpTempDir() throws IOException {
            tempDir = Files.createTempDirectory("test-assemble");
            originalPath = tempDir.resolve("test.txt").toString();
        }

        @Test
        void 无章节直接返回() throws Exception {
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(Collections.emptyList());

            invokeAssembleCompleteDocument(1L);

            verify(documentMapper, never()).selectById(anyLong());
        }

        @Test
        void 项目未关联Document记录警告() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(null);
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            invokeAssembleCompleteDocument(1L);

            verify(documentMapper, never()).selectById(anyLong());
        }

        @Test
        void 关联的Document不存在记录警告() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(99L);
            project.setOwnerId(1L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabProjectMapper.selectById(1L)).thenReturn(project);
            when(documentMapper.selectById(99L)).thenReturn(null);

            invokeAssembleCompleteDocument(1L);

            // Should not attempt file operations
            verify(translationTaskMapper, never()).insert(any());
        }

        @Test
        void 正常流程按章节号排序拼接并保存() throws Exception {
            // Create chapters out of order (use mutable ArrayList for sort)
            List<CollabChapterTask> chapters = new ArrayList<>();

            CollabChapterTask chapter2 = createChapter(2L, 1L, "Chapter two text");
            chapter2.setChapterNumber(2);
            chapter2.setTargetText("第二章译文");
            chapters.add(chapter2);

            CollabChapterTask chapter1 = createChapter(1L, 1L, "Chapter one text");
            chapter1.setChapterNumber(1);
            chapter1.setTargetText("第一章译文");
            chapters.add(chapter1);

            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentMapper.selectById(10L)).thenReturn(doc);
            when(translationTaskMapper.findByDocumentId(10L)).thenReturn(null);

            invokeAssembleCompleteDocument(1L);

            // Verify file was written with chapters in correct order
            Path translatedPath = Path.of(
                    com.yumu.noveltranslator.util.ExternalResponseUtil.buildTranslatedPath(originalPath));
            assertTrue(Files.exists(translatedPath));
            String content = Files.readString(translatedPath);
            assertTrue(content.contains("第一章译文"));
            assertTrue(content.contains("第二章译文"));
            // Chapter 1 should appear before Chapter 2
            assertTrue(content.indexOf("第一章译文") < content.indexOf("第二章译文"));

            // Verify TranslationTask was created
            verify(translationTaskMapper).insert(argThat(task ->
                    task.getUserId().equals(5L) && "completed".equals(task.getStatus())));

            // Verify Document status updated
            verify(documentMapper).updateById(argThat(d -> "completed".equals(d.getStatus())));
        }

        @Test
        void 跳过空译文章节() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();

            CollabChapterTask chapter1 = createChapter(1L, 1L, "Text");
            chapter1.setChapterNumber(1);
            chapter1.setTargetText("第一章");
            chapters.add(chapter1);

            CollabChapterTask chapter2 = createChapter(2L, 1L, "Text");
            chapter2.setChapterNumber(2);
            chapter2.setTargetText(null); // empty target
            chapters.add(chapter2);

            CollabChapterTask chapter3 = createChapter(3L, 1L, "Text");
            chapter3.setChapterNumber(3);
            chapter3.setTargetText("第三章");
            chapters.add(chapter3);

            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentMapper.selectById(10L)).thenReturn(doc);
            when(translationTaskMapper.findByDocumentId(10L)).thenReturn(null);

            invokeAssembleCompleteDocument(1L);

            Path translatedPath = Path.of(
                    com.yumu.noveltranslator.util.ExternalResponseUtil.buildTranslatedPath(originalPath));
            String content = Files.readString(translatedPath);
            assertTrue(content.contains("第一章"));
            assertTrue(content.contains("第三章"));
            assertFalse(content.contains("null"));
        }

        @Test
        void 所有章节译文为空不保存文件() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Text");
            chapter.setChapterNumber(1);
            chapter.setTargetText("");
            chapters.add(chapter);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentMapper.selectById(10L)).thenReturn(doc);

            invokeAssembleCompleteDocument(1L);

            // Should not create translation task or update document
            verify(translationTaskMapper, never()).insert(any());
            verify(documentMapper, never()).updateById(any());
        }

        @Test
        void 已存在TranslationTask则更新状态() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setChapterNumber(1);
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentMapper.selectById(10L)).thenReturn(doc);

            TranslationTask existingTask = new TranslationTask();
            existingTask.setId(100L);
            existingTask.setTaskId("existing_task");
            when(translationTaskMapper.findByDocumentId(10L)).thenReturn(existingTask);

            invokeAssembleCompleteDocument(1L);

            verify(translationTaskMapper, never()).insert(any());
            verify(translationTaskMapper).updateById(argThat(task ->
                    task.getId().equals(100L) && "completed".equals(task.getStatus())));
        }

        private void invokeAssembleCompleteDocument(Long projectId) throws Exception {
            java.lang.reflect.Method method = MultiAgentTranslationService.class
                    .getDeclaredMethod("assembleCompleteDocument", Long.class);
            method.setAccessible(true);
            method.invoke(service, projectId);
        }
    }

    private CollabChapterTask createChapter(Long id, Long projectId, String sourceText) {
        CollabChapterTask chapter = new CollabChapterTask();
        chapter.setId(id);
        chapter.setProjectId(projectId);
        chapter.setSourceText(sourceText);
        chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
        chapter.setChapterNumber(1);
        return chapter;
    }

    // ============ deriveNovelType ============

    @Nested
    @DisplayName("deriveNovelType - 小说类型推导")
    class DeriveNovelTypeTests {

        @Test
        void null项目返回daily() {
            String result = callDeriveNovelType(null);
            assertEquals("daily", result);
        }

        @Test
        void 空描述返回daily() {
            assertEquals("daily", callDeriveNovelType(""));
        }

        @Test
        void 奇幻关键词返回fantasy() {
            assertEquals("fantasy", callDeriveNovelType("这是一个玄幻故事"));
            assertEquals("fantasy", callDeriveNovelType("A fantasy adventure"));
        }

        @Test
        void 言情关键词返回romance() {
            assertEquals("romance", callDeriveNovelType("这是一个言情故事"));
            assertEquals("romance", callDeriveNovelType("A romance novel"));
        }

        @Test
        void 科幻关键词返回scifi() {
            assertEquals("scifi", callDeriveNovelType("scifi story"));
            assertEquals("scifi", callDeriveNovelType("科幻小说"));
        }

        @Test
        void 悬疑关键词返回mystery() {
            assertEquals("mystery", callDeriveNovelType("mystery novel"));
            assertEquals("mystery", callDeriveNovelType("悬疑推理"));
        }

        @Test
        void 恐怖关键词返回horror() {
            assertEquals("horror", callDeriveNovelType("horror story"));
            assertEquals("horror", callDeriveNovelType("恐怖小说"));
        }

        @Test
        void 日常关键词返回daily() {
            assertEquals("daily", callDeriveNovelType("日常故事"));
            assertEquals("daily", callDeriveNovelType("daily life"));
        }

        @Test
        void 无匹配关键词返回daily() {
            assertEquals("daily", callDeriveNovelType("Some random description"));
        }

        private String callDeriveNovelType(String description) {
            CollabProject project = new CollabProject();
            project.setDescription(description);
            return (String) ReflectionTestUtils.invokeMethod(service, "deriveNovelType", project);
        }
    }

    // ============ buildBaseCacheKey ============

    @Nested
    @DisplayName("buildBaseCacheKey - 缓存键构建")
    class BuildBaseCacheKeyTests {

        @Test
        void 相同文本生成相同键() {
            String key1 = (String) ReflectionTestUtils.invokeMethod(service, "buildBaseCacheKey", "Hello World", "zh");
            String key2 = (String) ReflectionTestUtils.invokeMethod(service, "buildBaseCacheKey", "Hello World", "zh");
            assertEquals(key1, key2);
        }

        @Test
        void 不同目标语言生成不同键() {
            String keyZh = (String) ReflectionTestUtils.invokeMethod(service, "buildBaseCacheKey", "Hello", "zh");
            String keyJa = (String) ReflectionTestUtils.invokeMethod(service, "buildBaseCacheKey", "Hello", "ja");
            assertNotEquals(keyZh, keyJa);
        }

        @Test
        void 键包含目标语言后缀() {
            String key = (String) ReflectionTestUtils.invokeMethod(service, "buildBaseCacheKey", "Test text", "en");
            assertTrue(key.endsWith("_en"));
        }
    }

    // ============ loadGlossaryTermsForProject ============

    @Nested
    @DisplayName("loadGlossaryTermsForProject - 术语表加载")
    class LoadGlossaryTermsTests {

        @Test
        void null用户返回空列表() {
            @SuppressWarnings("unchecked")
            List<Glossary> result = (List<Glossary>) ReflectionTestUtils.invokeMethod(
                    service, "loadGlossaryTermsForProject", null, "some text");
            assertTrue(result.isEmpty());
        }

        @Test
        void 过滤出原文中出现的术语() {
            Glossary term1 = new Glossary();
            term1.setSourceWord("hello");
            term1.setTargetWord("你好");

            Glossary term2 = new Glossary();
            term2.setSourceWord("world");
            term2.setTargetWord("世界");

            Glossary term3 = new Glossary();
            term3.setSourceWord("not_in_text");
            term3.setTargetWord("不在文本中");

            when(glossaryMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                    .thenReturn(List.of(term1, term2, term3));

            @SuppressWarnings("unchecked")
            List<Glossary> result = (List<Glossary>) ReflectionTestUtils.invokeMethod(
                    service, "loadGlossaryTermsForProject", 1L, "hello world text");

            assertEquals(2, result.size());
        }

        @Test
        void 术语源词为null时被过滤() {
            Glossary term1 = new Glossary();
            term1.setSourceWord(null);
            term1.setTargetWord("test");

            Glossary term2 = new Glossary();
            term2.setSourceWord("hello");
            term2.setTargetWord("你好");

            when(glossaryMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                    .thenReturn(List.of(term1, term2));

            @SuppressWarnings("unchecked")
            List<Glossary> result = (List<Glossary>) ReflectionTestUtils.invokeMethod(
                    service, "loadGlossaryTermsForProject", 1L, "hello");

            assertEquals(1, result.size());
            assertEquals("hello", result.get(0).getSourceWord());
        }

        @Test
        void 查询异常返回空列表() {
            when(glossaryMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                    .thenThrow(new RuntimeException("DB error"));

            @SuppressWarnings("unchecked")
            List<Glossary> result = (List<Glossary>) ReflectionTestUtils.invokeMethod(
                    service, "loadGlossaryTermsForProject", 1L, "text");

            assertTrue(result.isEmpty());
        }
    }

    // ============ recoverStuckChapters ============

    @Nested
    @DisplayName("recoverStuckChapters - 恢复中断章节")
    class RecoverStuckChaptersTests {

        @Test
        void 无中断章节直接返回() {
            when(chapterTaskMapper.selectByProjectIdAndStatus(anyLong(), eq("TRANSLATING")))
                    .thenReturn(List.of());

            service.startMultiAgentTranslation(1L);

            verify(chapterTaskMapper).selectByProjectIdAndStatus(1L, "TRANSLATING");
        }

        @Test
        void 超过最大重试次数的章节跳过() {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(10L);
            chapter.setProjectId(1L);
            chapter.setStatus("TRANSLATING");

            // 让章节达到最大重试次数（3次）
            for (int i = 0; i < 3; i++) {
                ReflectionTestUtils.invokeMethod(service, "incrementRetryCount", chapter);
            }

            when(chapterTaskMapper.selectByProjectIdAndStatus(eq(1L), eq("TRANSLATING")))
                    .thenReturn(List.of(chapter))
                    .thenReturn(List.of());
            when(chapterTaskMapper.selectByProjectIdAndStatus(eq(1L), eq("UNASSIGNED")))
                    .thenReturn(List.of());

            service.startMultiAgentTranslation(1L);

            // 超过重试次数的章节不应被回退
            assertEquals("TRANSLATING", chapter.getStatus());
        }

        @Test
        void 未超过重试次数的章节回退到UNASSIGNED() {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(20L);
            chapter.setProjectId(1L);
            chapter.setStatus("TRANSLATING");

            when(chapterTaskMapper.selectByProjectIdAndStatus(eq(1L), eq("TRANSLATING")))
                    .thenReturn(List.of(chapter))
                    .thenReturn(List.of());
            when(chapterTaskMapper.selectByProjectIdAndStatus(eq(1L), eq("UNASSIGNED")))
                    .thenReturn(List.of());

            service.startMultiAgentTranslation(1L);

            // 章节应被回退到 UNASSIGNED
            assertEquals("UNASSIGNED", chapter.getStatus());
            assertTrue(chapter.getReviewComment().contains("翻译中断"));
            verify(chapterTaskMapper).updateById(chapter);
        }
    }

    // ============ updateProjectProgress ============

    @Nested
    @DisplayName("updateProjectProgress - 项目进度更新")
    class UpdateProjectProgressTests {

        @Test
        void 全部完成更新进度为100() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setProgress(50);

            CollabChapterTask task1 = new CollabChapterTask();
            task1.setStatus("COMPLETED");
            CollabChapterTask task2 = new CollabChapterTask();
            task2.setStatus("COMPLETED");

            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of(task1, task2));
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            assertEquals(100, project.getProgress());
            verify(collabProjectMapper).updateById(project);
        }

        @Test
        void 部分完成计算正确百分比() {
            CollabProject project = new CollabProject();
            project.setId(1L);

            CollabChapterTask task1 = new CollabChapterTask();
            task1.setStatus("COMPLETED");
            CollabChapterTask task2 = new CollabChapterTask();
            task2.setStatus("SUBMITTED");
            CollabChapterTask task3 = new CollabChapterTask();
            task3.setStatus("UNASSIGNED");

            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of(task1, task2, task3));
            when(collabProjectMapper.selectById(1L)).thenReturn(project);

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            assertEquals(33, project.getProgress());
        }

        @Test
        void 无章节时不更新() {
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of());

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            verify(collabProjectMapper, never()).selectById(anyLong());
        }

        @Test
        void 项目不存在时不更新() {
            CollabChapterTask task = new CollabChapterTask();
            task.setStatus("COMPLETED");

            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of(task));
            when(collabProjectMapper.selectById(1L)).thenReturn(null);

            assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L));
        }
    }

    // ============ applyTranslationResult ============

    @Nested
    @DisplayName("applyTranslationResult - 翻译结果应用")
    class ApplyTranslationResultTests {

        @Test
        void 缓存命中标记为COMPLETED() {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(1L);

            ReflectionTestUtils.invokeMethod(service, "applyTranslationResult",
                    chapter, "source text", "translated text", true);

            assertEquals("translated text", chapter.getTargetText());
            assertEquals(100, chapter.getProgress());
            assertEquals("COMPLETED", chapter.getStatus());
            assertNotNull(chapter.getCompletedTime());
        }

        @Test
        void 新翻译标记为SUBMITTED() {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(2L);

            ReflectionTestUtils.invokeMethod(service, "applyTranslationResult",
                    chapter, "hello", "你好", false);

            assertEquals("你好", chapter.getTargetText());
            assertEquals(80, chapter.getProgress());
            assertEquals("SUBMITTED", chapter.getStatus());
            assertNotNull(chapter.getSubmittedTime());
        }
    }
}
