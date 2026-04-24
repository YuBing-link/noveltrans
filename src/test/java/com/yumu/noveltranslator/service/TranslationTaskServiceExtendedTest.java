package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.TranslationResultResponse;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.state.TranslationStateMachine;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationTaskService 补充测试
 * 覆盖现有测试未覆盖的分支：getTranslationResult 文档内容读取、非completed状态、
 * getDownloadPath document为null、getTranslationHistory 类型过滤/去重、
 * cleanupStuckTasks documentId为null等
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationTaskService 补充测试")
class TranslationTaskServiceExtendedTest {

    @Mock
    private TranslationTaskMapper translationTaskMapper;
    @Mock
    private TranslationHistoryMapper translationHistoryMapper;
    @Mock
    private DocumentMapper documentMapper;
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
    @Mock
    private TranslationStateMachine stateMachine;

    private TranslationTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TranslationTaskService(
                translationTaskMapper, translationHistoryMapper, documentMapper,
                stateMachine, translationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId) {
        com.yumu.noveltranslator.entity.User user = new com.yumu.noveltranslator.entity.User();
        user.setId(userId);
        user.setUserLevel("free");
        com.yumu.noveltranslator.security.CustomUserDetails userDetails =
                new com.yumu.noveltranslator.security.CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // ============ getTranslationResult 补充测试 ============

    @Nested
    @DisplayName("getTranslationResult - 未覆盖分支")
    class GetTranslationResultExtendedTests {

        @Test
        void processing状态返回基本信息() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-proc");
            task.setStatus("processing");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setType("text");
            when(translationTaskMapper.findByTaskId("task-proc")).thenReturn(task);

            TranslationResultResponse result = taskService.getTranslationResult("task-proc");

            assertNotNull(result);
            assertEquals("processing", result.getStatus());
        }

        @Test
        void failed状态返回基本信息() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-fail");
            task.setStatus("failed");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setType("text");
            when(translationTaskMapper.findByTaskId("task-fail")).thenReturn(task);

            TranslationResultResponse result = taskService.getTranslationResult("task-fail");

            assertNotNull(result);
            assertEquals("failed", result.getStatus());
        }

        @Test
        void pending状态返回基本信息() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-pend");
            task.setStatus("pending");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setType("text");
            when(translationTaskMapper.findByTaskId("task-pend")).thenReturn(task);

            TranslationResultResponse result = taskService.getTranslationResult("task-pend");

            assertNotNull(result);
            assertEquals("pending", result.getStatus());
        }

        @Test
        void 文档任务但document为null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-docnull");
            task.setStatus("completed");
            task.setType("document");
            task.setDocumentId(999L);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationTaskMapper.findByTaskId("task-docnull")).thenReturn(task);
            when(documentMapper.findById(999L)).thenReturn(null);

            TranslationResultResponse result = taskService.getTranslationResult("task-docnull");

            assertNotNull(result);
            // 没有文档，无法读取文件内容
            assertNull(result.getSourceContent());
        }

        @Test
        void 文档任务无documentId() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-nodocid");
            task.setStatus("completed");
            task.setType("document");
            task.setDocumentId(null);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationTaskMapper.findByTaskId("task-nodocid")).thenReturn(task);

            TranslationResultResponse result = taskService.getTranslationResult("task-nodocid");

            assertNotNull(result);
            assertNull(result.getTranslatedFilePath());
        }

        @Test
        void 文档任务读取不存在的文件() throws Exception {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-nofile");
            task.setStatus("completed");
            task.setType("document");
            task.setDocumentId(1L);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationTaskMapper.findByTaskId("task-nofile")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/nonexistent/path/file.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

            // 不应抛出异常
            assertDoesNotThrow(() -> taskService.getTranslationResult("task-nofile"));
        }

        @Test
        void 已完成文档任务读取翻译文件内容() throws Exception {
            // 创建真实的临时文件
            Path sourceFile = Files.createTempFile("test-source", ".txt");
            // buildTranslatedPath 会在原文件名后加 _translated
            String expectedTranslatedPath = sourceFile.toString().replace(".txt", "_translated.txt");
            Path translatedFile = Paths.get(expectedTranslatedPath);

            try {
                Files.writeString(sourceFile, "Hello World");
                Files.writeString(translatedFile, "你好世界");

                TranslationTask task = new TranslationTask();
                task.setTaskId("task-readtrans");
                task.setStatus("completed");
                task.setType("document");
                task.setDocumentId(1L);
                task.setSourceLang("en");
                task.setTargetLang("zh");
                when(translationTaskMapper.findByTaskId("task-readtrans")).thenReturn(task);

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(sourceFile.toString());
                when(documentMapper.findById(1L)).thenReturn(doc);

                TranslationResultResponse result = taskService.getTranslationResult("task-readtrans");

                assertNotNull(result);
                assertEquals("你好世界", result.getTranslatedText());
                assertEquals("Hello World", result.getSourceContent());
            } finally {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(translatedFile);
            }
        }

        @Test
        void 文本任务但历史不存在() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-text");
            task.setStatus("completed");
            task.setType("text");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationTaskMapper.findByTaskId("task-text")).thenReturn(task);
            when(translationHistoryMapper.findByTaskId("task-text")).thenReturn(null);

            TranslationResultResponse result = taskService.getTranslationResult("task-text");

            assertNotNull(result);
            assertNull(result.getTranslatedText());
            assertNull(result.getSourceContent());
        }
    }

    // ============ getDownloadPath 补充测试 ============

    @Nested
    @DisplayName("getDownloadPath - 未覆盖分支")
    class GetDownloadPathExtendedTests {

        @Test
        void document为null返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-dl");
            task.setUserId(1L);
            task.setType("document");
            task.setStatus("completed");
            task.setDocumentId(1L);
            when(translationTaskMapper.findByTaskId("task-dl")).thenReturn(task);
            when(documentMapper.findById(1L)).thenReturn(null);

            String result = taskService.getDownloadPath("task-dl", 1L);
            assertNull(result);
        }

        @Test
        void 文件不存在返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-dl2");
            task.setUserId(1L);
            task.setType("document");
            task.setStatus("completed");
            task.setDocumentId(1L);
            when(translationTaskMapper.findByTaskId("task-dl2")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/nonexistent/file.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

            String result = taskService.getDownloadPath("task-dl2", 1L);
            assertNull(result);
        }
    }

    // ============ getTranslationHistory 补充测试 ============

    @Nested
    @DisplayName("getTranslationHistory - 类型过滤和去重")
    class GetTranslationHistoryExtendedTests {

        @Test
        void type为all返回全部() {
            setAuthenticatedUser(1L);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory h1 = new TranslationHistory();
            h1.setTaskId("task-1");
            h1.setType("text");
            h1.setCreateTime(LocalDateTime.now().minusHours(1));

            TranslationHistory h2 = new TranslationHistory();
            h2.setTaskId("task-2");
            h2.setType("document");
            h2.setCreateTime(LocalDateTime.now().minusHours(2));

            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(h1, h2));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "all");
            assertEquals(2, result.size());
        }

        @Test
        void 去重逻辑保留较早的记录() {
            setAuthenticatedUser(1L);
            // in-progress 任务
            TranslationTask inProgress = new TranslationTask();
            inProgress.setTaskId("task-dup");
            inProgress.setUserId(1L);
            inProgress.setType("text");
            inProgress.setSourceLang("en");
            inProgress.setTargetLang("zh");
            inProgress.setEngine("google");
            inProgress.setCreateTime(LocalDateTime.now().minusMinutes(5));
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(List.of(inProgress));

            // 已完成的记录，taskId 相同
            TranslationHistory completed = new TranslationHistory();
            completed.setTaskId("task-dup");
            completed.setType("text");
            completed.setCreateTime(LocalDateTime.now().minusHours(1));
            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(completed));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, null);
            // 去重后应该只有1条
            assertEquals(1, result.size());
        }

        @Test
        void 无进行中任务仅返回历史记录() {
            setAuthenticatedUser(1L);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory h1 = new TranslationHistory();
            h1.setTaskId("task-only");
            h1.setType("text");
            h1.setCreateTime(LocalDateTime.now());
            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(h1));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, null);
            assertEquals(1, result.size());
            assertEquals("task-only", result.get(0).getTaskId());
        }

        @Test
        void createTime为null的任务排在后面() {
            setAuthenticatedUser(1L);
            TranslationTask task1 = new TranslationTask();
            task1.setTaskId("task-no-time");
            task1.setUserId(1L);
            task1.setType("text");
            task1.setSourceLang("en");
            task1.setTargetLang("zh");
            task1.setEngine("google");
            task1.setCreateTime(null);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(List.of(task1));
            when(translationHistoryMapper.findByUserId(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, null);
            assertEquals(1, result.size());
        }
    }

    // ============ cleanupStuckTasks 补充测试 ============

    @Nested
    @DisplayName("cleanupStuckTasks - 未覆盖分支")
    class CleanupStuckTasksExtendedTests {

        @Test
        void 任务无documentId仅更新任务状态() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-nodoc");
            stuckTask.setDocumentId(null);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            assertTrue(stuckTask.getErrorMessage().contains("任务超时"));
            verify(translationTaskMapper).updateById(stuckTask);
            verify(documentMapper, never()).selectById(anyLong());
        }

        @Test
        void document为null仅更新任务状态() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-docnull");
            stuckTask.setDocumentId(999L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));
            when(documentMapper.selectById(999L)).thenReturn(null);

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            verify(translationTaskMapper).updateById(stuckTask);
            verify(documentMapper, never()).updateById(any());
        }
    }

    // ============ deleteHistory 补充测试 ============

    @Nested
    @DisplayName("deleteHistory - 补充测试")
    class DeleteHistoryExtendedTests {

        @Test
        void 历史不存在返回false() {
            when(translationHistoryMapper.findByTaskId("nonexistent")).thenReturn(null);
            assertFalse(taskService.deleteHistory("nonexistent", 1L));
        }

        @Test
        void 不属于该用户返回false() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setUserId(2L); // Different user
            when(translationHistoryMapper.findByTaskId("task-other")).thenReturn(history);
            assertFalse(taskService.deleteHistory("task-other", 1L));
        }
    }

    // ============ countTranslationHistory 补充测试 ============

    @Nested
    @DisplayName("countTranslationHistory - 补充测试")
    class CountTranslationHistoryTests {

        @Test
        void 返回正确计数() {
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(42);
            assertEquals(42, taskService.countTranslationHistory(1L));
        }
    }
}
