package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.TaskStatusResponse;
import com.yumu.noveltranslator.dto.TranslationHistoryResponse;
import com.yumu.noveltranslator.dto.TranslationResultResponse;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
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

import java.io.IOException;
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
    private TranslationTaskMapper translationTaskMapper;
    @Mock
    private TranslationHistoryMapper translationHistoryMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private GlossaryMapper glossaryMapper;
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
                translationTaskMapper, translationHistoryMapper, documentMapper, glossaryMapper,
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
                when(translationTaskMapper.findByTaskId("task-docx")).thenReturn(task);

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("docx");
                when(documentMapper.findById(1L)).thenReturn(doc);

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
                when(translationTaskMapper.findByTaskId("task-pdf")).thenReturn(task);

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("pdf");
                when(documentMapper.findById(1L)).thenReturn(doc);

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
                when(translationTaskMapper.findByTaskId("task-epub")).thenReturn(task);

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                doc.setFileType("epub");
                when(documentMapper.findById(1L)).thenReturn(doc);

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
            when(translationTaskMapper.findByTaskId("task-proc-doc")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/some/path.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

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
            when(translationTaskMapper.findByTaskId("task-failed-doc")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/some/path.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

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
            when(translationTaskMapper.findByTaskId("task-pend-text")).thenReturn(task);

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
            when(translationTaskMapper.findByTaskId("task-text-dl")).thenReturn(task);

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
            when(translationTaskMapper.findByTaskId("task-mock-dl")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/definitely/does/not/exist/file.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

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
            when(translationTaskMapper.findByTaskId("task-nodocid-dl")).thenReturn(task);

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
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory textH = new TranslationHistory();
            textH.setTaskId("task-text");
            textH.setType("text");
            textH.setCreateTime(LocalDateTime.now());

            TranslationHistory docH = new TranslationHistory();
            docH.setTaskId("task-doc");
            docH.setType("document");
            docH.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(textH, docH));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "text");
            assertEquals(1, result.size());
            assertEquals("text", result.get(0).getType());
        }

        @Test
        void type为document仅返回文档翻译() {
            setAuthenticatedUser(1L);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory textH = new TranslationHistory();
            textH.setTaskId("task-text");
            textH.setType("text");
            textH.setCreateTime(LocalDateTime.now());

            TranslationHistory docH = new TranslationHistory();
            docH.setTaskId("task-doc");
            docH.setType("document");
            docH.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(textH, docH));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "document");
            assertEquals(1, result.size());
            assertEquals("document", result.get(0).getType());
        }

        @Test
        void type为all不过滤() {
            setAuthenticatedUser(1L);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory h1 = new TranslationHistory();
            h1.setTaskId("task-1");
            h1.setType("text");
            h1.setCreateTime(LocalDateTime.now());

            TranslationHistory h2 = new TranslationHistory();
            h2.setTaskId("task-2");
            h2.setType("document");
            h2.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationHistoryMapper.findByUserId(1L, 0, 10))
                    .thenReturn(List.of(h1, h2));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "all");
            assertEquals(2, result.size());
        }

        @Test
        void 空结果返回空列表() {
            setAuthenticatedUser(1L);
            when(translationTaskMapper.findByUserIdAndStatus(anyLong(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());
            when(translationHistoryMapper.findByUserId(anyLong(), anyInt(), anyInt()))
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
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(new ArrayList<>());

            taskService.cleanupStuckTasks();

            verify(translationTaskMapper, never()).updateById(any());
            verify(documentMapper, never()).selectById(anyLong());
        }

        @Test
        void 任务无documentId仅更新任务() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-nodoc-stuck");
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
        void document为null仅更新任务() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-docnull-stuck");
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

        @Test
        void document非processing状态不更新文档() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-doc-completed");
            stuckTask.setDocumentId(5L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(5L);
            doc.setStatus("completed");
            when(documentMapper.selectById(5L)).thenReturn(doc);

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            verify(translationTaskMapper).updateById(stuckTask);
            verify(documentMapper, never()).updateById(doc);
        }

        @Test
        void document为processing状态同时更新文档() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-doc-proc");
            stuckTask.setDocumentId(7L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(7L);
            doc.setStatus("processing");
            when(documentMapper.selectById(7L)).thenReturn(doc);

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            assertEquals("failed", doc.getStatus());
            verify(documentMapper).updateById(doc);
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

            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(task1, task2));

            taskService.cleanupStuckTasks();

            assertEquals("failed", task1.getStatus());
            assertEquals("failed", task2.getStatus());
            verify(translationTaskMapper, times(2)).updateById(any());
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
            when(translationTaskMapper.findByTaskId("task-failed-cancel")).thenReturn(task);

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
            when(translationTaskMapper.findByTaskId("task-pending-nodoc")).thenReturn(task);

            boolean result = taskService.cancelTask("task-pending-nodoc", 1L);

            assertTrue(result);
            assertEquals("failed", task.getStatus());
            assertEquals("用户取消任务", task.getErrorMessage());
            verify(documentMapper, never()).selectById(anyLong());
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
            when(translationTaskMapper.findByTaskId("task-no-current")).thenReturn(null);

            // 注意：updateTaskProgress 是 protected，测试类在同包下可直接调用
            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertEquals("completed", task.getStatus());
            assertEquals(100, task.getProgress());
            assertNotNull(task.getCompletedTime());
            verify(translationTaskMapper).updateById(task);
        }

        @Test
        void current任务状态为failed但错误消息不匹配正常更新() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-other-failed");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-other-failed");
            current.setStatus("failed");
            current.setErrorMessage("网络错误"); // 不是"用户取消任务"
            when(translationTaskMapper.findByTaskId("task-other-failed")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertEquals("completed", task.getStatus());
            verify(translationTaskMapper).updateById(task);
        }

        @Test
        void completed状态设置完成时间() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-complete-time");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-complete-time");
            current.setStatus("processing");
            when(translationTaskMapper.findByTaskId("task-complete-time")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            assertNotNull(task.getCompletedTime());
            verify(translationTaskMapper).updateById(task);
        }

        @Test
        void nonCompleted状态不设置完成时间() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-no-complete-time");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-no-complete-time");
            current.setStatus("processing");
            when(translationTaskMapper.findByTaskId("task-no-complete-time")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.PROCESSING, 50, null);

            assertNull(task.getCompletedTime());
            verify(translationTaskMapper).updateById(task);
        }
    }

    // ============ startDocumentTranslation - null/invalid 分支 ============

    @Nested
    @DisplayName("startDocumentTranslation - null/invalid 分支")
    class StartDocumentTranslationCoverageTests {

        @Test
        void task和doc均为null() {
            taskService.startDocumentTranslation(null, null);
            verify(documentMapper, never()).updateById(any());
            verify(translationTaskMapper, never()).updateById(any());
        }

        @Test
        void processing状态不应重新启动() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-proc-start");
            task.setStatus("processing");
            Document doc = new Document();

            taskService.startDocumentTranslation(task, doc);

            // 只有 pending/failed 才能启动，processing 应被跳过
            verify(documentMapper, never()).updateById(any());
        }

        @Test
        void completed状态不应重新启动() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-completed-start");
            task.setStatus("completed");
            Document doc = new Document();

            taskService.startDocumentTranslation(task, doc);

            verify(documentMapper, never()).updateById(any());
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
            when(translationTaskMapper.findByTaskId("task-doc-name")).thenReturn(null);

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
            when(translationTaskMapper.findByTaskId("task-via-doc")).thenReturn(task);

            Document doc = new Document();
            doc.setId(10L);
            doc.setName("via-task-doc.txt");
            when(documentMapper.findById(10L)).thenReturn(doc);

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
            when(translationTaskMapper.findByTaskId("task-nodocid-fallback")).thenReturn(task);

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
            when(translationTaskMapper.findByTaskId("task-null-fallback")).thenReturn(null);

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
            when(translationTaskMapper.findByTaskId("task-status-check")).thenReturn(task);

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
            when(translationTaskMapper.findByTaskId("task-lookup")).thenReturn(task);

            TranslationTask result = taskService.getTaskByTaskId("task-lookup");
            assertNotNull(result);
            assertEquals("task-lookup", result.getTaskId());
        }

        @Test
        void getTaskByTaskId不存在返回null() {
            when(translationTaskMapper.findByTaskId("missing")).thenReturn(null);
            assertNull(taskService.getTaskByTaskId("missing"));
        }

        @Test
        void getTaskByDocumentId返回任务() {
            TranslationTask task = new TranslationTask();
            task.setDocumentId(42L);
            when(translationTaskMapper.findByDocumentId(42L)).thenReturn(task);

            TranslationTask result = taskService.getTaskByDocumentId(42L);
            assertNotNull(result);
            assertEquals(42L, result.getDocumentId());
        }

        @Test
        void getTaskByDocumentId不存在返回null() {
            when(translationTaskMapper.findByDocumentId(999L)).thenReturn(null);
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
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(0);
            assertEquals(0, taskService.countTranslationHistory(1L));
        }

        @Test
        void 返回正数() {
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(100);
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
