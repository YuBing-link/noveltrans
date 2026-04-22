package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
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
    private UserLevelThrottledTranslationClient translationClient;

    @Mock
    private TranslationCacheService cacheService;

    @Mock
    private RagTranslationService ragTranslationService;

    @Mock
    private EntityConsistencyService entityConsistencyService;

    @Mock
    private TranslationPostProcessingService postProcessingService;

    private MultiAgentTranslationService service;

    @BeforeEach
    void setUp() {
        service = new MultiAgentTranslationService(
                chapterTaskMapper, collabProjectMapper, documentMapper, translationTaskMapper,
                translationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService);
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
            verify(chapterTaskMapper, times(1)).selectByProjectIdAndStatus(anyLong(), anyString());
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
}
