package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.DocumentInfoResponse;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.state.TranslationStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private TranslationTaskMapper translationTaskMapper;

    @Mock
    private TranslationStateMachine stateMachine;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentMapper, translationTaskMapper, stateMachine);
        // 注入 uploadDir 字段
        try {
            java.lang.reflect.Field field = DocumentService.class.getDeclaredField("uploadDir");
            field.setAccessible(true);
            field.set(documentService, "/tmp/test-uploads");
        } catch (Exception e) {
            fail("Failed to set uploadDir: " + e.getMessage());
        }
    }

    @Nested
    @DisplayName("获取用户文档列表")
    class GetUserDocumentsTests {

        @Test
        void 所有状态返回全部文档() {
            Document doc1 = new Document();
            doc1.setId(1L);
            doc1.setStatus("completed");
            Document doc2 = new Document();
            doc2.setId(2L);
            doc2.setStatus("pending");
            when(documentMapper.findByUserId(1L)).thenReturn(Arrays.asList(doc1, doc2));

            List<Document> result = documentService.getUserDocuments(1L, "all");

            assertEquals(2, result.size());
        }

        @Test
        void 按状态过滤() {
            Document doc1 = new Document();
            doc1.setId(1L);
            doc1.setStatus("completed");
            Document doc2 = new Document();
            doc2.setId(2L);
            doc2.setStatus("pending");
            when(documentMapper.findByUserId(1L)).thenReturn(Arrays.asList(doc1, doc2));

            List<Document> result = documentService.getUserDocuments(1L, "completed");

            assertEquals(1, result.size());
            assertEquals("completed", result.get(0).getStatus());
        }

        @Test
        void null状态返回全部() {
            Document doc1 = new Document();
            doc1.setId(1L);
            doc1.setStatus("completed");
            when(documentMapper.findByUserId(1L)).thenReturn(Collections.singletonList(doc1));

            List<Document> result = documentService.getUserDocuments(1L, null);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("获取文档详情")
    class GetDocumentByIdTests {

        @Test
        void 找到文档返回文档并校验所有权() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setUserId(1L);
            when(documentMapper.findByIdAndUserId(1L, 1L)).thenReturn(doc);

            Document result = documentService.getDocumentById(1L, 1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(documentMapper).findByIdAndUserId(1L, 1L);
        }

        @Test
        void 文档不存在返回null() {
            when(documentMapper.findByIdAndUserId(999L, 1L)).thenReturn(null);

            Document result = documentService.getDocumentById(999L, 1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("删除文档")
    class DeleteDocumentTests {

        @Test
        void 成功删除文件和标记已删除() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setUserId(1L);
            doc.setPath("/tmp/test-file.txt");
            when(documentMapper.findByIdAndUserId(1L, 1L)).thenReturn(doc);
            when(documentMapper.updateDeletedStatus(1L)).thenReturn(1);

            boolean result = documentService.deleteDocument(1L, 1L);

            assertTrue(result);
            verify(documentMapper).updateDeletedStatus(1L);
        }

        @Test
        void 文档不存在返回false() {
            when(documentMapper.findByIdAndUserId(999L, 1L)).thenReturn(null);

            boolean result = documentService.deleteDocument(999L, 1L);

            assertFalse(result);
            verify(documentMapper, never()).updateDeletedStatus(anyLong());
        }
    }

    @Nested
    @DisplayName("重新翻译文档")
    class RetryTranslationTests {

        @Test
        void 成功重置文档和任务状态() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setUserId(1L);
            doc.setStatus("failed");
            doc.setErrorMessage("翻译失败");
            when(documentMapper.findByIdAndUserId(1L, 1L)).thenReturn(doc);
            when(documentMapper.updateById(any(Document.class))).thenReturn(1);

            TranslationTask task = new TranslationTask();
            task.setId(1L);
            task.setDocumentId(1L);
            task.setStatus("failed");
            when(translationTaskMapper.selectOne(any())).thenReturn(task);

            boolean result = documentService.retryTranslation(1L, 1L);

            assertTrue(result);
            assertEquals("pending", doc.getStatus());
            assertNull(doc.getErrorMessage());
            assertEquals("pending", task.getStatus());
            assertEquals(0, task.getProgress());
            assertNull(task.getErrorMessage());
        }

        @Test
        void 文档不存在返回false() {
            when(documentMapper.findByIdAndUserId(999L, 1L)).thenReturn(null);

            boolean result = documentService.retryTranslation(999L, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("转换为DocumentInfoResponse")
    class ToDocumentInfoResponseTests {

        @Test
        void null输入返回null() {
            DocumentInfoResponse result = documentService.toDocumentInfoResponse(null);
            assertNull(result);
        }

        @Test
        void 完成状态进度为100() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setName("test.txt");
            doc.setFileType("txt");
            doc.setFileSize(1000L);
            doc.setSourceLang("en");
            doc.setTargetLang("zh");
            doc.setStatus("completed");
            doc.setCreateTime(LocalDateTime.now());
            doc.setCompletedTime(LocalDateTime.now());

            DocumentInfoResponse result = documentService.toDocumentInfoResponse(doc);

            assertNotNull(result);
            assertEquals(100, result.getProgress());
            assertEquals("completed", result.getStatus());
        }

        @Test
        void 处理中状态使用任务进度() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setName("test.txt");
            doc.setFileType("txt");
            doc.setFileSize(1000L);
            doc.setSourceLang("en");
            doc.setTargetLang("zh");
            doc.setStatus("processing");
            doc.setTaskId("task-123");
            doc.setCreateTime(LocalDateTime.now().minusMinutes(1));

            TranslationTask task = new TranslationTask();
            task.setTaskId("task-123");
            task.setProgress(45);
            when(translationTaskMapper.findByTaskId("task-123")).thenReturn(task);

            DocumentInfoResponse result = documentService.toDocumentInfoResponse(doc);

            assertNotNull(result);
            assertEquals(45, result.getProgress());
        }

        @Test
        void 待处理状态使用时间进度() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setName("test.txt");
            doc.setFileType("txt");
            doc.setFileSize(1000L);
            doc.setSourceLang("en");
            doc.setTargetLang("zh");
            doc.setStatus("pending");
            doc.setCreateTime(LocalDateTime.now().minusMinutes(1));

            when(translationTaskMapper.findByTaskId(anyString())).thenReturn(null);

            DocumentInfoResponse result = documentService.toDocumentInfoResponse(doc);

            assertNotNull(result);
            // pending 且无任务进度，返回基于时间的进度（至少5%）
            assertTrue(result.getProgress() >= 5);
        }
    }

    @Nested
    @DisplayName("获取真实进度")
    class GetRealProgressTests {

        @Test
        void 待处理文档通过toDocumentInfoResponse间接计算时间进度() {
            Document doc = new Document();
            doc.setId(1L);
            doc.setName("test.txt");
            doc.setFileType("txt");
            doc.setFileSize(1000L);
            doc.setSourceLang("en");
            doc.setTargetLang("zh");
            doc.setStatus("pending");
            doc.setCreateTime(LocalDateTime.now().minusSeconds(60));

            when(translationTaskMapper.findByTaskId(anyString())).thenReturn(null);

            DocumentInfoResponse result = documentService.toDocumentInfoResponse(doc);

            assertNotNull(result);
            // pending 且无任务进度，返回基于时间的进度（至少5%）
            assertTrue(result.getProgress() >= 5);
            assertTrue(result.getProgress() <= 95);
        }
    }
}
