package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.out.translate.TeamTranslationService;
import com.yumu.noveltranslator.domain.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import com.yumu.noveltranslator.port.out.TranslationCachePort;
import com.yumu.noveltranslator.domain.service.AiGlossaryService;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;
import com.yumu.noveltranslator.domain.service.EntityConsistencyService;
import com.yumu.noveltranslator.domain.service.MultiAgentTranslationService;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.Glossary;
import com.yumu.noveltranslator.domain.model.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiAgentTranslationServiceTest {

    @Mock
    private CollaborationRepositoryPort collabPort;

    @Mock
    private DocumentRepositoryPort documentPort;

    @Mock
    private TranslationRepositoryPort translationPort;

    @Mock
    private TeamTranslationService teamTranslationService;

    @Mock
    private TranslationCachePort cachePort;

    @Mock
    private EntityConsistencyService entityConsistencyService;

    @Mock
    private GlossaryRepositoryPort glossaryPort;

    @Mock
    private RagTranslationApplicationService ragTranslationService;

    @Mock
    private AiGlossaryService aiGlossaryService;

    @Mock
    private TranslationPostProcessingService postProcessingService;

    @Mock
    private CollabStateMachine collabStateMachine;

    private MultiAgentTranslationService service;

    @BeforeEach
    void setUp() {
        service = new MultiAgentTranslationService(
                collabPort, documentPort, translationPort,
                teamTranslationService, cachePort, entityConsistencyService,
                glossaryPort, ragTranslationService, aiGlossaryService, postProcessingService,
                collabStateMachine, null);
    }

    @Nested
    @DisplayName("startMultiAgentTranslation - 前置校验")
    class StartValidationTests {

        @Test
        void 没有待翻译章节直接返回() {
            when(collabPort.findChapterTasksByProjectIdAndStatus(anyLong(), anyString()))
                    .thenReturn(Collections.emptyList());

            service.startMultiAgentTranslation(1L);

            verify(collabPort, never()).findProjectById(anyLong());
            verify(collabPort, atLeastOnce()).findChapterTasksByProjectIdAndStatus(anyLong(), anyString());
        }

        @Test
        void 项目不存在记录错误并返回() {
            List<CollabChapterTask> chapters = new ArrayList<>();
            chapters.add(createChapter(1L, 1L, "Some text"));
            when(collabPort.findChapterTasksByProjectIdAndStatus(anyLong(), anyString()))
                    .thenReturn(chapters);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());

            // Since virtual threads are involved, we just verify the port calls
            service.startMultiAgentTranslation(1L);

            verify(collabPort).findProjectById(1L);
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
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(Collections.emptyList());

            invokeAssembleCompleteDocument(1L);

            verify(documentPort, never()).findById(anyLong());
        }

        @Test
        void 项目未关联Document记录警告() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(null);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            invokeAssembleCompleteDocument(1L);

            verify(documentPort, never()).findById(anyLong());
        }

        @Test
        void 关联的Document不存在记录警告() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(99L);
            project.setOwnerId(1L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(documentPort.findById(99L)).thenReturn(Optional.empty());

            invokeAssembleCompleteDocument(1L);

            // Should not attempt file operations
            verify(translationPort, never()).saveTask(any());
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

            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentPort.findById(10L)).thenReturn(Optional.of(doc));
            when(translationPort.findTaskByDocumentId(10L)).thenReturn(Optional.empty());

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
            verify(translationPort).saveTask(argThat(task ->
                    task.getUserId().equals(5L) && "completed".equals(task.getStatus())));

            // Verify Document status updated
            verify(documentPort).update(argThat(d -> "completed".equals(d.getStatus())));
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

            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentPort.findById(10L)).thenReturn(Optional.of(doc));
            when(translationPort.findTaskByDocumentId(10L)).thenReturn(Optional.empty());

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
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentPort.findById(10L)).thenReturn(Optional.of(doc));

            invokeAssembleCompleteDocument(1L);

            // Should not create translation task or update document
            verify(translationPort, never()).saveTask(any());
            verify(documentPort, never()).update(any());
        }

        @Test
        void 已存在TranslationTask则更新状态() throws Exception {
            List<CollabChapterTask> chapters = new ArrayList<>();
            CollabChapterTask chapter = createChapter(1L, 1L, "Hello");
            chapter.setChapterNumber(1);
            chapter.setTargetText("你好");
            chapters.add(chapter);
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(chapters);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setDocumentId(10L);
            project.setOwnerId(5L);
            project.setSourceLang("en");
            project.setTargetLang("zh");
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            Document doc = new Document();
            doc.setId(10L);
            doc.setPath(originalPath);
            when(documentPort.findById(10L)).thenReturn(Optional.of(doc));

            TranslationTask existingTask = new TranslationTask();
            existingTask.setId(100L);
            existingTask.setTaskId("existing_task");
            when(translationPort.findTaskByDocumentId(10L)).thenReturn(Optional.of(existingTask));

            invokeAssembleCompleteDocument(1L);

            verify(translationPort, never()).saveTask(any());
            verify(translationPort).updateTask(argThat(task ->
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

    // buildBaseCacheKey 方法已删除，相关测试已移除

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

            when(glossaryPort.findActiveGlossaryByUserId(1L))
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

            when(glossaryPort.findActiveGlossaryByUserId(1L))
                    .thenReturn(List.of(term1, term2));

            @SuppressWarnings("unchecked")
            List<Glossary> result = (List<Glossary>) ReflectionTestUtils.invokeMethod(
                    service, "loadGlossaryTermsForProject", 1L, "hello");

            assertEquals(1, result.size());
            assertEquals("hello", result.get(0).getSourceWord());
        }

        @Test
        void 查询异常返回空列表() {
            when(glossaryPort.findActiveGlossaryByUserId(1L))
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

        @BeforeEach
        void setUpStateMachine() {
            doAnswer(inv -> {
                CollabChapterTask t = inv.getArgument(0);
                ChapterTaskStatus target = inv.getArgument(1);
                t.setStatus(target.getValue());
                return null;
            }).when(collabStateMachine).transitionChapter(any(), any(ChapterTaskStatus.class));
        }

        @Test
        void 无中断章节直接返回() {
            when(collabPort.findChapterTasksByProjectIdAndStatus(anyLong(), eq("TRANSLATING")))
                    .thenReturn(List.of());

            service.startMultiAgentTranslation(1L);

            verify(collabPort).findChapterTasksByProjectIdAndStatus(1L, "TRANSLATING");
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

            when(collabPort.findChapterTasksByProjectIdAndStatus(eq(1L), eq("TRANSLATING")))
                    .thenReturn(List.of(chapter))
                    .thenReturn(List.of());
            when(collabPort.findChapterTasksByProjectIdAndStatus(eq(1L), eq("UNASSIGNED")))
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

            when(collabPort.findChapterTasksByProjectIdAndStatus(eq(1L), eq("TRANSLATING")))
                    .thenReturn(List.of(chapter))
                    .thenReturn(List.of());
            when(collabPort.findChapterTasksByProjectIdAndStatus(eq(1L), eq("UNASSIGNED")))
                    .thenReturn(List.of());

            service.startMultiAgentTranslation(1L);

            // 章节应被回退到 UNASSIGNED
            assertEquals("UNASSIGNED", chapter.getStatus());
            assertTrue(chapter.getReviewComment().contains("翻译中断"));
            verify(collabPort).updateChapterTask(chapter);
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

            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of(task1, task2));
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            assertEquals(100, project.getProgress());
            verify(collabPort).updateProject(project);
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

            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of(task1, task2, task3));
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            assertEquals(33, project.getProgress());
        }

        @Test
        void 无章节时不更新() {
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of());

            ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L);

            verify(collabPort, never()).findProjectById(anyLong());
        }

        @Test
        void 项目不存在时不更新() {
            CollabChapterTask task = new CollabChapterTask();
            task.setStatus("COMPLETED");

            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of(task));
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(service, "updateProjectProgress", 1L));
        }
    }

    // ============ applyTranslationResult ============

    @Nested
    @DisplayName("applyTranslationResult - 翻译结果应用")
    class ApplyTranslationResultTests {

        @BeforeEach
        void setUpStateMachine() {
            doAnswer(inv -> {
                CollabChapterTask t = inv.getArgument(0);
                ChapterTaskStatus target = inv.getArgument(1);
                t.setStatus(target.getValue());
                return null;
            }).when(collabStateMachine).transitionChapter(any(), any(ChapterTaskStatus.class));
        }

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
