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
import com.yumu.noveltranslator.service.pipeline.TranslationPipeline;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationTaskServiceTest {

    @Mock
    private TranslationTaskMapper translationTaskMapper;

    @Mock
    private TranslationHistoryMapper translationHistoryMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private GlossaryMapper glossaryMapper;

    @Mock
    private TranslationStateMachine stateMachine;

    @Mock
    private UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;

    @Mock
    private TranslationCacheService cacheService;

    @Mock
    private RagTranslationService ragTranslationService;

    @Mock
    private EntityConsistencyService entityConsistencyService;

    @Mock
    private TranslationPostProcessingService postProcessingService;

    private TranslationTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TranslationTaskService(
                translationTaskMapper, translationHistoryMapper, documentMapper, glossaryMapper,
                stateMachine, userLevelThrottledTranslationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService);
    }

    @Nested
    @DisplayName("创建文档翻译任务")
    class CreateDocumentTaskTests {

        @Test
        void 创建任务并生成taskId() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setSourceLang("en");
            doc.setTargetLang("zh");
            doc.setMode("expert");

            TranslationTask task = taskService.createDocumentTask(1L, doc);

            assertNotNull(task);
            assertNotNull(task.getTaskId());
            assertTrue(task.getTaskId().startsWith("task_"));
            assertEquals("document", task.getType());
            assertEquals(1L, task.getUserId());
            assertEquals(1L, task.getDocumentId());
            assertEquals(0, task.getProgress());
            verify(translationTaskMapper).insert(any(TranslationTask.class));
            verify(documentMapper).updateById(doc);
            assertEquals(task.getTaskId(), doc.getTaskId());
        }
    }

    @Nested
    @DisplayName("根据任务ID获取任务")
    class GetTaskByTaskIdTests {

        @Test
        void 找到返回任务() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setStatus("pending");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            TranslationTask result = taskService.getTaskByTaskId("task-123");

            assertNotNull(result);
            assertEquals("task-123", result.getTaskId());
        }

        @Test
        void 未找到返回null() {
            when(translationTaskMapper.findByTaskId("nonexistent")).thenReturn(null);

            TranslationTask result = taskService.getTaskByTaskId("nonexistent");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("根据文档ID获取任务")
    class GetTaskByDocumentIdTests {

        @Test
        void 找到返回任务() {
            TranslationTask task = new TranslationTask();
            task.setId(1L);
            task.setDocumentId(5L);
            when(translationTaskMapper.findByDocumentId(5L)).thenReturn(task);

            TranslationTask result = taskService.getTaskByDocumentId(5L);

            assertNotNull(result);
            assertEquals(5L, result.getDocumentId());
        }

        @Test
        void 未找到返回null() {
            when(translationTaskMapper.findByDocumentId(999L)).thenReturn(null);

            TranslationTask result = taskService.getTaskByDocumentId(999L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("取消翻译任务")
    class CancelTaskTests {

        @Test
        void pending任务可取消() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setUserId(1L);
            task.setStatus("pending");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            boolean result = taskService.cancelTask("task-123", 1L);

            assertTrue(result);
            assertEquals("failed", task.getStatus());
            assertEquals("用户取消任务", task.getErrorMessage());
            verify(translationTaskMapper).updateById(task);
        }

        @Test
        void processing任务可取消() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setUserId(1L);
            task.setStatus("processing");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            boolean result = taskService.cancelTask("task-123", 1L);

            assertTrue(result);
            assertEquals("用户取消任务", task.getErrorMessage());
        }

        @Test
        void 任务不存在返回false() {
            when(translationTaskMapper.findByTaskId("nonexistent")).thenReturn(null);

            boolean result = taskService.cancelTask("nonexistent", 1L);

            assertFalse(result);
        }

        @Test
        void 不属于该用户返回false() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setUserId(2L);
            task.setStatus("pending");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            boolean result = taskService.cancelTask("task-123", 1L);

            assertFalse(result);
        }

        @Test
        void 已完成任务不能取消() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setUserId(1L);
            task.setStatus("completed");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            boolean result = taskService.cancelTask("task-123", 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("删除翻译历史")
    class DeleteHistoryTests {

        @Test
        void 成功删除() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setUserId(1L);
            history.setTaskId("task-123");
            when(translationHistoryMapper.findByTaskId("task-123")).thenReturn(history);

            boolean result = taskService.deleteHistory("task-123", 1L);

            assertTrue(result);
            verify(translationHistoryMapper).deleteById(1L);
        }

        @Test
        void 历史不存在返回false() {
            when(translationHistoryMapper.findByTaskId("nonexistent")).thenReturn(null);

            boolean result = taskService.deleteHistory("nonexistent", 1L);

            assertFalse(result);
        }

        @Test
        void 不属于该用户返回false() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setUserId(2L);
            when(translationHistoryMapper.findByTaskId("task-123")).thenReturn(history);

            boolean result = taskService.deleteHistory("task-123", 1L);

            assertFalse(result);
            verify(translationHistoryMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("获取翻译结果")
    class GetTranslationResultTests {

        @Test
        void 已完成的文本任务返回翻译文本() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setType("text");
            task.setStatus("completed");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setCompletedTime(LocalDateTime.now());
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            TranslationHistory history = new TranslationHistory();
            history.setTargetText("翻译结果");
            history.setSourceText("Hello World");
            when(translationHistoryMapper.findByTaskId("task-123")).thenReturn(history);

            TranslationResultResponse result = taskService.getTranslationResult("task-123");

            assertNotNull(result);
            assertEquals("completed", result.getStatus());
            assertEquals("翻译结果", result.getTranslatedText());
            assertEquals("Hello World", result.getSourceContent());
        }

        @Test
        void 已完成的文档任务返回文件路径() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-doc");
            task.setType("document");
            task.setStatus("completed");
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setDocumentId(1L);
            task.setCompletedTime(LocalDateTime.now());
            when(translationTaskMapper.findByTaskId("task-doc")).thenReturn(task);

            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("/tmp/test.txt");
            when(documentMapper.findById(1L)).thenReturn(doc);

            TranslationResultResponse result = taskService.getTranslationResult("task-doc");

            assertNotNull(result);
            assertEquals("/tmp/test.txt", result.getTranslatedFilePath());
        }

        @Test
        void 任务不存在返回null() {
            when(translationTaskMapper.findByTaskId("nonexistent")).thenReturn(null);

            TranslationResultResponse result = taskService.getTranslationResult("nonexistent");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("获取下载路径")
    class GetDownloadPathTests {

        @Test
        void 成功返回路径() throws Exception {
            // 创建临时文件以满足 Files.exists 检查
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test-download", ".txt");
            try {
                TranslationTask task = new TranslationTask();
                task.setTaskId("task-doc");
                task.setUserId(1L);
                task.setType("document");
                task.setStatus("completed");
                task.setDocumentId(1L);
                when(translationTaskMapper.findByTaskId("task-doc")).thenReturn(task);

                Document doc = new Document();
                doc.setId(1L);
                doc.setPath(tempFile.toString());
                when(documentMapper.findById(1L)).thenReturn(doc);

                String result = taskService.getDownloadPath("task-doc", 1L);

                assertEquals(tempFile.toString(), result);
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void 任务不存在返回null() {
            when(translationTaskMapper.findByTaskId("nonexistent")).thenReturn(null);

            String result = taskService.getDownloadPath("nonexistent", 1L);

            assertNull(result);
        }

        @Test
        void 不属于该用户返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-doc");
            task.setUserId(2L);
            when(translationTaskMapper.findByTaskId("task-doc")).thenReturn(task);

            String result = taskService.getDownloadPath("task-doc", 1L);

            assertNull(result);
        }

        @Test
        void 未完成返回null() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-doc");
            task.setUserId(1L);
            task.setType("document");
            task.setStatus("pending");
            when(translationTaskMapper.findByTaskId("task-doc")).thenReturn(task);

            String result = taskService.getDownloadPath("task-doc", 1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("转换为TaskStatusResponse")
    class ToTaskStatusResponseTests {

        @Test
        void null输入返回null() {
            TaskStatusResponse result = taskService.toTaskStatusResponse(null);
            assertNull(result);
        }

        @Test
        void 完整转换() {
            LocalDateTime createTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
            LocalDateTime completedTime = LocalDateTime.of(2024, 1, 15, 10, 35, 0);
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setType("document");
            task.setStatus("completed");
            task.setProgress(100);
            task.setSourceLang("en");
            task.setTargetLang("zh");
            task.setCreateTime(createTime);
            task.setCompletedTime(completedTime);
            task.setErrorMessage(null);

            TaskStatusResponse result = taskService.toTaskStatusResponse(task);

            assertNotNull(result);
            assertEquals("task-123", result.getTaskId());
            assertEquals("document", result.getType());
            assertEquals("completed", result.getStatus());
            assertEquals(100, result.getProgress());
            assertEquals("en", result.getSourceLang());
            assertEquals("zh", result.getTargetLang());
            assertNotNull(result.getCreateTime());
            assertNotNull(result.getCompletedTime());
        }
    }

    @Nested
    @DisplayName("转换为TranslationHistoryResponse")
    class ToHistoryResponseTests {

        @Test
        void null输入返回null() {
            TranslationHistoryResponse result = taskService.toHistoryResponse(null);
            assertNull(result);
        }

        @Test
        void 带任务查询() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-123");
            history.setType("text");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setSourceText("Hello World, this is a long text for testing purposes");
            history.setTargetText("你好世界，这是一段很长的测试文本");
            history.setCreateTime(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(null);

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("task-123", result.getTaskId());
            assertEquals("text", result.getType());
            assertEquals("completed", result.getStatus());
            assertNotNull(result.getSourceTextPreview());
            assertNotNull(result.getTargetTextPreview());
            assertEquals("文本翻译", result.getDocumentName());
        }

        @Test
        void 关联文档名称() {
            TranslationHistory history = new TranslationHistory();
            history.setId(1L);
            history.setTaskId("task-doc");
            history.setType("document");
            history.setSourceLang("en");
            history.setTargetLang("zh");
            history.setDocumentId(5L);
            history.setCreateTime(LocalDateTime.now());
            when(translationTaskMapper.findByTaskId("task-doc")).thenReturn(null);

            Document doc = new Document();
            doc.setId(5L);
            doc.setName("novel.txt");
            when(documentMapper.findById(5L)).thenReturn(doc);

            TranslationHistoryResponse result = taskService.toHistoryResponse(history);

            assertNotNull(result);
            assertEquals("novel.txt", result.getDocumentName());
        }
    }

    @Nested
    @DisplayName("更新任务进度")
    class UpdateTaskProgressTests {

        @Test
        void 正常更新() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setStatus("pending");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-123");
            current.setStatus("processing");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.PROCESSING, 50, null);

            assertEquals("processing", task.getStatus());
            assertEquals(50, task.getProgress());
            verify(translationTaskMapper).updateById(task);
        }

        @Test
        void 已完成任务不改变状态() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");

            // 模拟任务已被用户取消
            TranslationTask current = new TranslationTask();
            current.setTaskId("task-123");
            current.setStatus("failed");
            current.setErrorMessage("用户取消任务");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);

            // 由于任务被取消，不应调用 updateById
            verify(translationTaskMapper, never()).updateById(task);
        }

        @Test
        void 错误消息更新() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setStatus("pending");

            TranslationTask current = new TranslationTask();
            current.setTaskId("task-123");
            current.setStatus("processing");
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(current);

            taskService.updateTaskProgress(task, TranslationStatus.FAILED, 0, "翻译出错");

            assertEquals("翻译出错", task.getErrorMessage());
            verify(translationTaskMapper).updateById(task);
        }
    }

    @Nested
    @DisplayName("统计翻译历史")
    class CountTranslationHistoryTests {

        @Test
        void 委托给mapper() {
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(25);

            int result = taskService.countTranslationHistory(1L);

            assertEquals(25, result);
            verify(translationHistoryMapper).countByUserId(1L);
        }
    }

    @Nested
    @DisplayName("启动文档翻译")
    class StartDocumentTranslationTests {

        @Test
        void task为null直接返回() {
            taskService.startDocumentTranslation(null, new Document());
            verify(translationTaskMapper, never()).updateById(any());
        }

        @Test
        void doc为null直接返回() {
            taskService.startDocumentTranslation(new TranslationTask(), null);
            verify(translationTaskMapper, never()).updateById(any());
        }

        @Test
        void 已完成状态不启动() {
            TranslationTask task = new TranslationTask();
            task.setStatus("completed");
            Document doc = new Document();

            taskService.startDocumentTranslation(task, doc);

            verify(documentMapper, never()).updateById(any());
        }

        @Test
        void pending状态可启动() throws Exception {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-1");
            task.setStatus("pending");
            Document doc = new Document();
            doc.setId(1L);
            doc.setPath("test.txt");
            doc.setFileType("txt");

            // Need real file for readDocumentContent
            java.nio.file.Path tempFile = Files.createTempFile("start-test", ".txt");
            Files.writeString(tempFile, "Hello World");
            doc.setPath(tempFile.toString());

            try {
                taskService.startDocumentTranslation(task, doc);

                assertEquals("processing", doc.getStatus());
                verify(documentMapper).updateById(doc);
                // Async thread will handle the rest, just verify the state update
            } finally {
                Files.deleteIfExists(java.nio.file.Paths.get(doc.getPath()));
            }
        }

        @Test
        void failed状态可重试启动() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-1");
            task.setStatus("failed");
            Document doc = new Document();
            doc.setId(1L);

            taskService.startDocumentTranslation(task, doc);

            assertEquals("processing", doc.getStatus());
            verify(documentMapper).updateById(doc);
        }
    }

    @Nested
    @DisplayName("取消任务")
    class CancelTaskAdvancedTests {

        @Test
        void 取消任务同步更新文档状态() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-1");
            task.setUserId(1L);
            task.setStatus("processing");
            task.setDocumentId(5L);
            when(translationTaskMapper.findByTaskId("task-1")).thenReturn(task);

            Document doc = new Document();
            doc.setId(5L);
            doc.setStatus("processing");
            when(documentMapper.selectById(5L)).thenReturn(doc);

            boolean result = taskService.cancelTask("task-1", 1L);

            assertTrue(result);
            assertEquals("用户取消任务", task.getErrorMessage());
            // Verify document status was also updated
            verify(documentMapper).updateById(doc);
            assertEquals("用户取消任务", doc.getErrorMessage());
        }

        @Test
        void 无文档ID取消不更新文档() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-1");
            task.setUserId(1L);
            task.setStatus("pending");
            task.setDocumentId(null);
            when(translationTaskMapper.findByTaskId("task-1")).thenReturn(task);

            boolean result = taskService.cancelTask("task-1", 1L);

            assertTrue(result);
            verify(documentMapper, never()).selectById(anyLong());
        }

        @Test
        void 文档不存在不报错() {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-1");
            task.setUserId(1L);
            task.setStatus("processing");
            task.setDocumentId(5L);
            when(translationTaskMapper.findByTaskId("task-1")).thenReturn(task);
            when(documentMapper.selectById(5L)).thenReturn(null);

            boolean result = taskService.cancelTask("task-1", 1L);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("获取翻译历史列表")
    class GetTranslationHistoryTests {

        @Test
        void 返回进行中的任务和已完成历史() {
            TranslationTask inProgressTask = new TranslationTask();
            inProgressTask.setUserId(1L);
            inProgressTask.setTaskId("task-1");
            inProgressTask.setType("document");
            inProgressTask.setDocumentId(1L);
            inProgressTask.setSourceLang("en");
            inProgressTask.setTargetLang("zh");
            inProgressTask.setEngine("google");
            inProgressTask.setCreateTime(LocalDateTime.now().minusMinutes(5));
            when(translationTaskMapper.findByUserIdAndStatus(eq(1L), anyInt(), anyInt()))
                    .thenReturn(List.of(inProgressTask));

            TranslationHistory completedHistory = new TranslationHistory();
            completedHistory.setId(1L);
            completedHistory.setUserId(1L);
            completedHistory.setTaskId("task-2");
            completedHistory.setType("text");
            completedHistory.setSourceLang("en");
            completedHistory.setTargetLang("zh");
            completedHistory.setCreateTime(LocalDateTime.now().minusHours(1));
            when(translationHistoryMapper.findByUserId(eq(1L), anyInt(), anyInt()))
                    .thenReturn(List.of(completedHistory));

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, null);

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        void 类型过滤只返回文本翻译() {
            when(translationTaskMapper.findByUserIdAndStatus(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            TranslationHistory textHistory = new TranslationHistory();
            textHistory.setId(1L);
            textHistory.setUserId(1L);
            textHistory.setTaskId("task-1");
            textHistory.setType("text");
            textHistory.setSourceLang("en");
            textHistory.setTargetLang("zh");
            textHistory.setCreateTime(LocalDateTime.now());
            when(translationHistoryMapper.findByUserId(eq(1L), anyInt(), anyInt()))
                    .thenReturn(List.of(textHistory));

            TranslationHistory docHistory = new TranslationHistory();
            docHistory.setId(2L);
            docHistory.setUserId(1L);
            docHistory.setTaskId("task-2");
            docHistory.setType("document");
            docHistory.setSourceLang("en");
            docHistory.setTargetLang("zh");
            docHistory.setCreateTime(LocalDateTime.now().minusMinutes(1));
            // Note: histories are added via the mock return, not via the taskMapper

            List<TranslationHistory> result = taskService.getTranslationHistory(1L, 1, 10, "text");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("text", result.get(0).getType());
        }
    }

    @Nested
    @DisplayName("清理卡死任务")
    class CleanupStuckTasksTests {

        @Test
        void 超时任务标记为失败() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-stuck");
            stuckTask.setDocumentId(1L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(1L);
            doc.setStatus("processing");
            when(documentMapper.selectById(1L)).thenReturn(doc);

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            assertTrue(stuckTask.getErrorMessage().contains("任务超时"));
            verify(translationTaskMapper).updateById(stuckTask);
            verify(documentMapper).updateById(doc);
            assertEquals("failed", doc.getStatus());
        }

        @Test
        void 无超时任务不更新() {
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(new ArrayList<>());

            taskService.cleanupStuckTasks();

            verify(translationTaskMapper, never()).updateById(any());
        }

        @Test
        void 文档非处理中状态不更新() {
            TranslationTask stuckTask = new TranslationTask();
            stuckTask.setTaskId("task-stuck");
            stuckTask.setDocumentId(1L);
            stuckTask.setCreateTime(LocalDateTime.now().minusMinutes(31));
            when(translationTaskMapper.findByStatusAndCreateTimeBefore(anyString(), any(LocalDateTime.class)))
                    .thenReturn(List.of(stuckTask));

            Document doc = new Document();
            doc.setId(1L);
            doc.setStatus("completed"); // Not processing
            when(documentMapper.selectById(1L)).thenReturn(doc);

            taskService.cleanupStuckTasks();

            assertEquals("failed", stuckTask.getStatus());
            verify(documentMapper, never()).updateById(doc);
        }
    }
}
