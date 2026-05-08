package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.out.security.CustomUserDetails;
import com.yumu.noveltranslator.adapter.out.translate.UserLevelThrottledTranslationClient;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.application.service.TranslationTaskApplicationService;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;
import com.yumu.noveltranslator.domain.service.EntityConsistencyService;

import com.yumu.noveltranslator.port.dto.entity.TaskStatusResponse;
import com.yumu.noveltranslator.port.dto.entity.TranslationHistoryResponse;
import com.yumu.noveltranslator.port.dto.translation.TranslationResultResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationHistory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationCachePort;
import com.yumu.noveltranslator.domain.service.TranslationStateMachine;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationTaskService 覆盖率补充测试
 * 覆盖 ExtendedTest 未覆盖的分支：
 * - readDocumentContent 不支持的文件格式 (DOCX/EPUB/PDF)
 * - getTranslationResult 所有非 completed 状态分支 (pending/processing/failed 走同一主分支)
 * - getDownloadPath 非文档类型任务、文件不存在 MockedStatic 路径
 * - getTranslationHistory 多种 type 过滤组合
 * - cleanupStuckTasks 空列表、document 非 processing 状态
 * - cancelTask 边界条件
 * - updateTaskProgress current 为 null 的分支
 * - startDocumentTranslation null/invalid 分支
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationTaskService 覆盖率补充测试")
class TranslationTaskServiceCoverageTest {

    @Mock
    private TranslationRepositoryPort translationPort;
    @Mock
    private DocumentRepositoryPort documentPort;
    @Mock
    private GlossaryRepositoryPort glossaryPort;
    @Mock
    private UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;
    @Mock
    private TranslationCachePort cachePort;
    @Mock
    private RagTranslationApplicationService ragTranslationService;
    @Mock
    private EntityConsistencyService entityConsistencyService;
    @Mock
    private TranslationPostProcessingService postProcessingService;
    @Mock
    private TranslationStateMachine stateMachine;

    private TranslationTaskApplicationService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TranslationTaskApplicationService(
                translationPort, documentPort, glossaryPort,
                stateMachine, userLevelThrottledTranslationClient, cachePort, ragTranslationService,
                entityConsistencyService, postProcessingService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId) {
        com.yumu.noveltranslator.adapter.out.persistence.entity.User user = new com.yumu.noveltranslator.adapter.out.persistence.entity.User();
        user.setId(userId);
        user.setUserLevel("free");
        com.yumu.noveltranslator.adapter.out.security.CustomUserDetails userDetails =
                new com.yumu.noveltranslator.adapter.out.security.CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // ============ readDocumentContent 不支持的格式 ============

    @Nested
    @DisplayName("readDocumentContent - 不支持的文件格式")
    class ReadDocumentContentTests {

        @Test
        void docx格式抛出IOException() throws Exception {
            // 创建真实临时文件让 Files.exists 通过
            Path tempFile = Files.createTempFile("test", ".docx");
            try {
                TranslationTask task = new TranslationTask();
                task.setTaskId("task-docx");
                task.setStatus("completed");
                task.setType("document");
                task.setDocumentId(1L);
                task.setSourceLang("en");
                task.setTargetLang("zh");
                when(translationPort.findTaskByTaskId("task-docx")).thenReturn(Optional.of(task));

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("docx");
                when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

                // getTranslationResult 内部 catch 了 Exception，不应抛出
                TranslationResultResponse result = taskService.getTranslationResult("task-docx");
                assertNotNull(result);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void pdf格式抛出IOException() throws Exception {
            Path tempFile = Files.createTempFile("test", ".pdf");
            try {
                TranslationTask task = new TranslationTask();
                task.setTaskId("task-pdf");
                task.setStatus("completed");
                task.setType("document");
                task.setDocumentId(1L);
                task.setSourceLang("en");
                task.setTargetLang("zh");
                when(translationPort.findTaskByTaskId("task-pdf")).thenReturn(Optional.of(task));

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("pdf");
                when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

                TranslationResultResponse result = taskService.getTranslationResult("task-pdf");
                assertNotNull(result);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void epub格式抛出IOException() throws Exception {
            Path tempFile = Files.createTempFile("test", ".epub");
            try {
                TranslationTask task = new TranslationTask();
                task.setTaskId("task-epub");
                task.setStatus("completed");
                task.setType("document");
                task.setDocumentId(1L);
                task.setSourceLang("en");
                task.setTargetLang("zh");
                when(translationPort.findTaskByTaskId("task-epub")).thenReturn(Optional.of(task));

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("epub");
                when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

                TranslationResultResponse result = taskService.getTranslationResult("task-epub");
                assertNotNull(result);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    // ============ getTranslationResult - 非 completed 状态分支 ============

    @Nested
    @DisplayName("getTranslationResult - 各状态分支")
    class GetTranslationResultStatusTests {

        @Test
        void processing状态文档任务返回基本响应() throws Exception {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-proc-doc");
            task.setStatus("processing");
            task.setType("document");
            task.setDocumentId(1L);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationPort.findTaskByTaskId("task-proc-doc")).thenReturn(Optional.of(task));

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/some/path.txt");
            when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

            TranslationResultResponse result = taskService.getTranslationResult("task-proc-doc");
            assertNotNull(result);
            assertEquals("processing", result.getStatus());
            assertEquals("/some/path.txt", result.getTranslatedFilePath());
            // processing 状态不应读取翻译文件内容
            assertNull(result.getTranslatedText());
        }

        @Test
        void failed状态文档任务返回基本响应() throws Exception {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-failed-doc");
            task.setStatus("failed");
            task.setType("document");
            task.setDocumentId(1L);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setErrorMessage("翻译出错");
            when(translationPort.findTaskByTaskId("task-failed-doc")).thenReturn(Optional.of(task));

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/some/path.txt");
            when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

            TranslationResultResponse result = taskService.getTranslationResult("task-failed-doc");
            assertNotNull(result);
            assertEquals("failed", result.getStatus());
            assertNull(result.getTranslatedText());
        }

        @Test
        void pending状态文本任务返回基本响应() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-pend-text");
            task.setStatus("pending");
            task.setType("text");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            when(translationPort.findTaskByTaskId("task-pend-text")).thenReturn(Optional.of(task));

            TranslationResultResponse result = taskService.getTranslationResult("task-pend-text");
            assertNotNull(result);
            assertEquals("pending", result.getStatus());
            assertNull(result.getTranslatedText());
        }
    }

    // ============ getDownloadPath - 更多边界 ============

    @Nested
    @DisplayName("getDownloadPath - 边界条件")
    class GetDownloadPathCoverageTests {

        @Test
        void 文本类型任务返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-text-dl");
            task.setUserId(1L);
            task.setType("text");
            task.setStatus("completed");
            when(translationPort.findTaskByTaskId("task-text-dl")).thenReturn(Optional.of(task));

            String result = taskService.getDownloadPath("task-text-dl", 1L);
            assertNull(result);
        }

        @Test
        void 文件不存在通过MockedStatic返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-mock-dl");
            task.setUserId(1L);
            task.setType("document");
            task.setStatus("completed");
            task.setDocumentId(1L);
            when(translationPort.findTaskByTaskId("task-mock-dl")).thenReturn(Optional.of(task));

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/definitely/does/not/exist/file.txt");
            when(documentPort.findById(1L)).thenReturn(Optional.of(doc));

            try (var mockedFiles = mockStatic(Files.class)) {
                Path fakePath = Paths.get("/definitely/does/not/exist/file.txt");
                mockedFiles.when(() -> Files.exists(fakePath)).thenReturn(false);

                String result = taskService.getDownloadPath("task-mock-dl", 1L);
                assertNull(result);
            }
        }

        @Test
        void documentId为null返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-nodocid-dl");
            task.setUserId(1L);
            task.setType("document");
            task.setStatus("completed");
            task.setDocumentId(null);
            when(translationPort.findTaskByTaskId("task-nodocid-dl")).thenReturn(Optional.of(task));

            String result = taskService.getDownloadPath("task-nodocid-dl", 1L);
            assertNull(result);
        }
    }

    // ============ getTranslationHistory - type 过滤组合 ============

    @Nested
    @DisplayName("getTranslationHistory - type 过滤组合")
    class GetTranslationHistoryTypeFilterTests {

        @Test
        void type为text仅返回文本翻译() {
            setAuthenticatedUser(1L);
            when(translationPort.findTasksByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory textH = new TranslationHistory();
            textH.setTaskId("task-text");
            textH.setType("text");
            textH.setCreateTime(LocalDateTime.now());

            TranslationHistory docH = new TranslationHistory();
            docH.setTaskId("task-doc");
            docH.setType("document");
            docH.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationPort.findHistoryByUserId(1L, 0, 10))
                    .thenReturn(List.of(textH, docH));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "text");
            assertEquals(1, result.size());
            assertEquals("text", result.get(0).getType());
        }

        @Test
        void type为document仅返回文档翻译() {
            setAuthenticatedUser(1L);
            when(translationPort.findTasksByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory textH = new TranslationHistory();
            textH.setTaskId("task-text");
            textH.setType("text");
            textH.setCreateTime(LocalDateTime.now());

            TranslationHistory docH = new TranslationHistory();
            docH.setTaskId("task-doc");
            docH.setType("document");
            docH.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationPort.findHistoryByUserId(1L, 0, 10))
                    .thenReturn(List.of(textH, docH));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "document");
            assertEquals(1, result.size());
            assertEquals("document", result.get(0).getType());
        }

        @Test
        void type为all不过滤() {
            setAuthenticatedUser(1L);
            when(translationPort.findTasksByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory h1 = new TranslationHistory();
            h1.setTaskId("task-1");
            h1.setType("text");
            h1.setCreateTime(LocalDateTime.now());

            TranslationHistory h2 = new TranslationHistory();
            h2.setTaskId("task-2");
            h2.setType("document");
            h2.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationPort.findHistoryByUserId(1L, 0, 10))
                    .thenReturn(List.of(h1, h2));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "all");
            assertEquals(2, result.size());
        }

        @Test
        void 空结果返回空列表() {
            setAuthenticatedUser(1L);
            when(translationPort.findTasksByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());
            when(translationPort.findHistoryByUserId(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "text");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ============ cleanupStuckTasks - 更多场景 ============

    @Nested
    @DisplayName("cleanupStuckTasks - 更多场景")
    class CleanupStuckTasksCoverageTests {

        @Test
        void 空列表不执行任何更新() {
            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(new ArrayList<>());

            taskService.cleanupStuckTasks();

            verify(translationPort, never()).updateTask(any());
            verify(documentPort, never()).findById(anyLong());
        }

        @Test
        void 任务无documentId仅更新任务() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-nodoc-stuck");
            stuckTask.setDocumentId(null);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            assertTrue(stuckTask.getErrorMessage().contains("任务超时"));
            verify(translationPort).updateTask(stuckTask);
            verify(documentPort, never()).findById(anyLong());
        }

        @Test
        void document为null仅更新任务() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-docnull-stuck");
            stuckTask.setDocumentId(999L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));
            when(documentPort.findById(999L)).thenReturn(Optional.empty());

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            verify(translationPort).updateTask(stuckTask);
            verify(documentPort, never()).update(any());
        }

        @Test
        void document非processing状态不更新文档() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-doc-completed");
            stuckTask.setDocumentId(5L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(5L);
            doc.setStatus("completed");
            when(documentPort.findById(5L)).thenReturn(Optional.of(doc));

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            verify(translationPort).updateTask(stuckTask);
            verify(documentPort, never()).update(doc);
        }

        @Test
        void document为processing状态同时更新文档() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-doc-proc");
            stuckTask.setDocumentId(7L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(7L);
            doc.setStatus("processing");
            when(documentPort.findById(7L)).thenReturn(Optional.of(doc));

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            assertEquals("failed", doc.getStatus());
            verify(documentPort).update(doc);
        }

        @Test
        void 多个超时任务批量处理() {
            TranslationTask task1 = new TranslationTask();
            task1.setTaskId("task-stuck-1");
            task1.setDocumentId(null);
            task1.setCreateTime(LocalDateTime.now().minusMinutes(31));

            TranslationTask task2 = new TranslationTask();
            task2.setTaskId("task-stuck-2");
            task2.setDocumentId(null);
            task2.setCreateTime(LocalDateTime.now().minusMinutes(45));

            when(translationPort.findTasksByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(task1, task2));

            taskService.cleanupStuckTasks();

            assertEquals("failed", task1.getStatus());
            assertEquals("failed", task2.getStatus());
            verify(translationPort, times(2)).updateTask(any());
        }
    }

    // ============ cancelTask - 更多边界 ============

    @Nested
    @DisplayName("cancelTask - 边界条件")
    class CancelTaskCoverageTests {

        @Test
        void failed任务不能取消() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-failed-cancel");
            task.setUserId(1L);
            task.setStatus("failed");
            when(translationPort.findTaskByTaskId("task-failed-cancel")).thenReturn(Optional.of(task));

            boolean result = taskService.cancelTask("task-failed-cancel", 1L);
            assertFalse(result);
        }

        @Test
        void pending任务无文档ID取消成功() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-pending-nodoc");
            task.setUserId(1L);
            task.setStatus("pending");
            task.setDocumentId(null);
            when(translationPort.findTaskByTaskId("task-pending-nodoc")).thenReturn(Optional.of(task));

            boolean result = taskService.cancelTask("task-pending-nodoc", 1L);

            assertTrue(result);
            assertEquals("failed", task.getStatus());
            assertEquals("用户取消任务", task.getErrorMessage());
            verify(documentPort, never()).findById(anyLong());
        }
    }

    // ============ updateTaskProgress - current 为 null 分支 ============

    @Nested
    @DisplayName("updateTaskProgress - current 为 null 分支")
    class UpdateTaskProgressCoverageTests {

        @Test
        void current任务为null正常更新() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-no-current");
            when(translationPort.findTaskByTaskId("task-no-current")).thenReturn(Optional.empty());

            // 注意：updateTaskProgress 是 protected，测试类在同包下可直接调用
            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertEquals("completed", task.getStatus());
            assertEquals(100, task.getProgress());
            assertNotNull(task.getCompletedTime());
            verify(translationPort).updateTask(task);
        }

        @Test
        void current任务状态为failed但错误消息不匹配正常更新() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-other-failed");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-other-failed");
            current.setStatus("failed");
            current.setErrorMessage("网络错误"); // 不是"用户取消任务"
            when(translationPort.findTaskByTaskId("task-other-failed")).thenReturn(Optional.of(current));

            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertEquals("completed", task.getStatus());
            verify(translationPort).updateTask(task);
        }

        @Test
        void completed状态设置完成时间() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-complete-time");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-complete-time");
            current.setStatus("processing");
            when(translationPort.findTaskByTaskId("task-complete-time")).thenReturn(Optional.of(current));

            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertNotNull(task.getCompletedTime());
            verify(translationPort).updateTask(task);
        }

        @Test
        void nonCompleted状态不设置完成时间() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-no-complete-time");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-no-complete-time");
            current.setStatus("processing");
            when(translationPort.findTaskByTaskId("task-no-complete-time")).thenReturn(Optional.of(current));

            taskService.updateTaskProgress(task, TranslationStatus.PROCESSING, 50, null);

            assertNull(task.getCompletedTime());
            verify(translationPort).updateTask(task);
        }
    }

    // ============ startDocumentTranslation - null/invalid 分支 ============

    @Nested
    @DisplayName("startDocumentTranslation - null/invalid 分支")
    class StartDocumentTranslationCoverageTests {

        @Test
        void task和doc均为null() {
            taskService.startDocumentTranslation(null, null);
            verify(documentPort, never()).update(any());
            verify(translationPort, never()).updateTask(any());
        }

        @Test
        void processing状态不应重新启动() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-proc-start");
            task.setStatus("processing");
            Document doc = new Document();

            taskService.startDocumentTranslation(task, doc);

            // 只有 pending/failed 才能启动，processing 应被跳过
            verify(documentPort, never()).update(any());
        }

        @Test
        void completed状态不应重新启动() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-completed-start");
            task.setStatus("completed");
            Document doc = new Document();

            taskService.startDocumentTranslation(task, doc);

            verify(documentPort, never()).update(any());
        }
    }

    // ============ toHistoryResponse - 更多分支 ============

    @Nested
    @DisplayName("toHistoryResponse - 更多分支")
    class ToHistoryResponseCoverageTests {

        @Test
        void 文档类型默认名称() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-doc-name");
            history.setType("document");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setDocumentId(null);
            history.setCreateTime(LocalDateTime.now());
            when(translationPort.findTaskByTaskId("task-doc-name")).thenReturn(Optional.empty());

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("文档翻译", result.getDocumentName());
        }

        @Test
        void 通过任务关联文档获取名称() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-via-doc");
            history.setType("document");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setDocumentId(null);
            history.setCreateTime(LocalDateTime.now());

            TranslationTask task = new TranslationTask();
            task.setTaskId("task-via-doc");
            task.setDocumentId(10L);
            when(translationPort.findTaskByTaskId("task-via-doc")).thenReturn(Optional.of(task));

            Document doc = new Document();
            doc.setId(10L);
            doc.setName("via-task-doc.txt");
            when(documentPort.findById(10L)).thenReturn(Optional.of(doc));

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("via-task-doc.txt", result.getDocumentName());
        }

        @Test
        void 任务关联文档但documentId为null使用默认名称() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-nodocid-fallback");
            history.setType("text");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setDocumentId(null);
            history.setCreateTime(LocalDateTime.now());

            TranslationTask task = new TranslationTask();
            task.setTaskId("task-nodocid-fallback");
            task.setDocumentId(null);
            when(translationPort.findTaskByTaskId("task-nodocid-fallback")).thenReturn(Optional.of(task));

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("文本翻译", result.getDocumentName());
        }

        @Test
        void 任务关联但任务为null使用默认名称() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-null-fallback");
            history.setType("text");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setDocumentId(null);
            history.setCreateTime(LocalDateTime.now());
            when(translationPort.findTaskByTaskId("task-null-fallback")).thenReturn(Optional.empty());

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("文本翻译", result.getDocumentName());
        }

        @Test
        void 通过任务查询状态completed() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-status-check");
            history.setType("text");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setCreateTime(LocalDateTime.now());

            TranslationTask task = new TranslationTask();
            task.setTaskId("task-status-check");
            task.setStatus("completed");
            when(translationPort.findTaskByTaskId("task-status-check")).thenReturn(Optional.of(task));

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("completed", result.getStatus());
        }
    }

    // ============ getTaskByTaskId / getTaskByDocumentId ============

    @Nested
    @DisplayName("getTaskByTaskId / getTaskByDocumentId")
    class GetTaskByIdCoverageTests {

        @Test
        void getTaskByTaskId返回任务() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-lookup");
            task.setStatus("processing");
            when(translationPort.findTaskByTaskId("task-lookup")).thenReturn(Optional.of(task));

            TranslationTask result = taskService.getTaskByTaskId("task-lookup");
            assertNotNull(result);
            assertEquals("task-lookup", result.getTaskId());
        }

        @Test
        void getTaskByTaskId不存在返回null() {
            when(translationPort.findTaskByTaskId("missing")).thenReturn(Optional.empty());
            assertNull(taskService.getTaskByTaskId("missing"));
        }

        @Test
        void getTaskByDocumentId返回任务() {
            TranslationTask task = new TranslationTask();
            task.setDocumentId(42L);
            when(translationPort.findTaskByDocumentId(42L)).thenReturn(Optional.of(task));

            TranslationTask result = taskService.getTaskByDocumentId(42L);
            assertNotNull(result);
            assertEquals(42L, result.getDocumentId());
        }

        @Test
        void getTaskByDocumentId不存在返回null() {
            when(translationPort.findTaskByDocumentId(999L)).thenReturn(Optional.empty());
            assertNull(taskService.getTaskByDocumentId(999L));
        }
    }

    // ============ toTaskStatusResponse ============

    @Nested
    @DisplayName("toTaskStatusResponse - 边界")
    class ToTaskStatusResponseCoverageTests {

        @Test
        void 无完成时间和错误消息() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-no-times");
            task.setType("text");
            task.setStatus("processing");
            task.setProgress(50);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setCreateTime(null);
            task.setCompletedTime(null);
            task.setErrorMessage(null);

            TaskStatusResponse result = taskService.toTaskStatusResponse(task);

            assertNotNull(result);
            assertNull(result.getCreateTime());
            assertNull(result.getCompletedTime());
            assertNull(result.getErrorMessage());
        }

        @Test
        void 有完成时间和错误消息() {
            LocalDateTime ct = LocalDateTime.of(2024, 6, 1, 12, 0, 0);
            LocalDateTime cmt = LocalDateTime.of(2024, 6, 1, 12, 5, 0);
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-times");
            task.setType("document");
            task.setStatus("completed");
            task.setProgress(100);
            task.setSourceLang("zh");
            task.setTargetLang("en");
            task.setCreateTime(ct);
            task.setCompletedTime(cmt);
            task.setErrorMessage("some error");

            TaskStatusResponse result = taskService.toTaskStatusResponse(task);

            assertNotNull(result);
            assertNotNull(result.getCreateTime());
            assertNotNull(result.getCompletedTime());
            assertEquals("some error", result.getErrorMessage());
        }
    }

    // ============ countTranslationHistory ============

    @Nested
    @DisplayName("countTranslationHistory")
    class CountTranslationHistoryCoverageTests {

        @Test
        void 返回零() {
            when(translationPort.countHistoryByUserId(1L)).thenReturn(0);
            assertEquals(0, taskService.countTranslationHistory(1L));
        }

        @Test
        void 返回正数() {
            when(translationPort.countHistoryByUserId(1L)).thenReturn(100);
            assertEquals(100, taskService.countTranslationHistory(1L));
        }
    }

    // ============ shutdown ============

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        void shutdown不抛异常() {
            assertDoesNotThrow(() -> taskService.shutdown());
        }
    }
}
