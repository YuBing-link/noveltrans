package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.CollabProjectService;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebDocumentControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private DocumentService documentService;

    @org.mockito.Mock
    private TranslationTaskService translationTaskService;

    @org.mockito.Mock
    private CollabProjectService collabProjectService;

    private WebDocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new WebDocumentController(documentService, translationTaskService, collabProjectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void setupSecurityContext() {
        com.yumu.noveltranslator.entity.User user = new com.yumu.noveltranslator.entity.User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Document createTestDocument() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setUserId(1L);
        doc.setName("test.txt");
        doc.setPath("/uploads/test.txt");
        doc.setFileType("txt");
        doc.setSourceLang("en");
        doc.setTargetLang("zh");
        doc.setStatus("pending");
        return doc;
    }

    @Nested
    @DisplayName("获取文档列表")
    class GetDocumentsTests {

        @Test
        void 获取文档列表成功() throws Exception {
            setupSecurityContext();
            Document doc = createTestDocument();
            when(documentService.getUserDocuments(eq(1L), eq("all"))).thenReturn(List.of(doc));

            DocumentInfoResponse infoResp = new DocumentInfoResponse();
            infoResp.setId(1L);
            infoResp.setName("test.txt");
            when(documentService.toDocumentInfoResponse(any())).thenReturn(infoResp);

            mockMvc.perform(get("/user/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        void 带分页和状态参数() throws Exception {
            setupSecurityContext();
            when(documentService.getUserDocuments(eq(1L), eq("completed"))).thenReturn(List.of());

            mockMvc.perform(get("/user/documents")
                    .param("page", "1")
                    .param("pageSize", "10")
                    .param("status", "completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取文档详情")
    class GetDocumentTests {

        @Test
        void 获取文档详情成功() throws Exception {
            setupSecurityContext();
            Document doc = createTestDocument();
            when(documentService.getDocumentById(1L, 1L)).thenReturn(doc);

            DocumentInfoResponse infoResp = new DocumentInfoResponse();
            infoResp.setId(1L);
            when(documentService.toDocumentInfoResponse(any())).thenReturn(infoResp);

            mockMvc.perform(get("/user/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        void 文档不存在返回错误() throws Exception {
            setupSecurityContext();
            when(documentService.getDocumentById(999L, 1L)).thenReturn(null);

            mockMvc.perform(get("/user/documents/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("文档不存在"));
        }
    }

    @Nested
    @DisplayName("删除文档")
    class DeleteDocumentTests {

        @Test
        void 删除文档成功() throws Exception {
            setupSecurityContext();
            when(documentService.deleteDocument(1L, 1L)).thenReturn(true);

            mockMvc.perform(delete("/user/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 删除失败返回错误() throws Exception {
            setupSecurityContext();
            when(documentService.deleteDocument(1L, 1L)).thenReturn(false);

            mockMvc.perform(delete("/user/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("删除失败"));
        }
    }

    @Nested
    @DisplayName("取消翻译")
    class CancelTranslationTests {

        @Test
        void 取消翻译成功() throws Exception {
            setupSecurityContext();
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-001");
            task.setUserId(1L);
            when(translationTaskService.getTaskByDocumentId(1L)).thenReturn(task);
            when(translationTaskService.cancelTask("task-001", 1L)).thenReturn(true);

            mockMvc.perform(post("/user/documents/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 任务不存在返回错误() throws Exception {
            setupSecurityContext();
            when(translationTaskService.getTaskByDocumentId(1L)).thenReturn(null);

            mockMvc.perform(post("/user/documents/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("翻译任务不存在"));
        }

        @Test
        void 无权操作返回错误() throws Exception {
            setupSecurityContext();
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-001");
            task.setUserId(2L);
            when(translationTaskService.getTaskByDocumentId(1L)).thenReturn(task);

            mockMvc.perform(post("/user/documents/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权操作"));
        }
    }

    @Nested
    @DisplayName("重新翻译")
    class RetryTranslationTests {

        @Test
        void 重新翻译成功() throws Exception {
            setupSecurityContext();
            when(documentService.retryTranslation(1L, 1L)).thenReturn(true);

            mockMvc.perform(post("/user/documents/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 重试失败返回错误() throws Exception {
            setupSecurityContext();
            when(documentService.retryTranslation(1L, 1L)).thenReturn(false);

            mockMvc.perform(post("/user/documents/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("重试失败，文档不存在"));
        }
    }

    @Nested
    @DisplayName("上传文档")
    class UploadDocumentTests {

        @Test
        void 上传文档成功_fast模式() throws Exception {
            setupSecurityContext();
            Document doc = createTestDocument();
            when(documentService.uploadDocument(eq(1L), any(), any())).thenReturn(doc);

            TranslationTask task = new TranslationTask();
            task.setTaskId("task-001");
            task.setStatus("pending");
            when(translationTaskService.createDocumentTask(eq(1L), any())).thenReturn(task);
            doNothing().when(translationTaskService).startDocumentTranslation(any(), any());

            MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello World".getBytes());

            mockMvc.perform(multipart("/user/documents/upload")
                    .file(file)
                    .param("sourceLang", "en")
                    .param("targetLang", "zh")
                    .param("mode", "fast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").value(1));
        }

        @Test
        void 上传文档_team模式() throws Exception {
            setupSecurityContext();
            Document doc = createTestDocument();
            when(documentService.uploadDocument(eq(1L), any(), any())).thenReturn(doc);

            CollabProjectService.TeamProjectCreateResult projectResult =
                new CollabProjectService.TeamProjectCreateResult(1L, "test.txt", 3);
            when(collabProjectService.createProjectFromDocument(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(projectResult);
            doNothing().when(collabProjectService).startMultiAgentTranslation(1L);

            MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello World".getBytes());

            mockMvc.perform(multipart("/user/documents/upload")
                    .file(file)
                    .param("sourceLang", "en")
                    .param("targetLang", "zh")
                    .param("mode", "team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.message").value("团队模式已创建项目，共 3 个章节"));
        }
    }

    @Nested
    @DisplayName("下载文档")
    class DownloadDocumentTests {

        @Test
        void 文档不存在返回404() throws Exception {
            setupSecurityContext();
            when(documentService.getDocumentById(1L, 1L)).thenReturn(null);

            mockMvc.perform(get("/user/documents/1/download"))
                .andExpect(status().isNotFound());
        }
    }
}
