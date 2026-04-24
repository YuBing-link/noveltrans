package com.yumu.noveltranslator.controller.shared;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SharedTranslateControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TranslationService translationService;

    @Mock
    private TranslationTaskService translationTaskService;

    @Mock
    private DocumentService documentService;

    @Mock
    private RagTranslationService ragTranslationService;

    private SharedTranslateController controller;

    @BeforeEach
    void setUp() {
        controller = new SharedTranslateController(translationService, translationTaskService, documentService, ragTranslationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // 文本翻译端点 (/v1/translate/text) 在 PluginTranslateController 中定义
    // 该控制器的测试请参见 PluginTranslateControllerTest

    @Nested
    @DisplayName("取消翻译任务")
    class CancelTaskTests {

        @Test
        void 取消任务成功() throws Exception {
            setupSecurityContext();
            when(translationTaskService.cancelTask("task-001", 1L)).thenReturn(true);

            mockMvc.perform(delete("/v1/translate/task/task-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 取消失败返回错误() throws Exception {
            setupSecurityContext();
            when(translationTaskService.cancelTask("task-002", 1L)).thenReturn(false);

            mockMvc.perform(delete("/v1/translate/task/task-002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("取消失败，任务可能已完成或正在处理"));
        }
    }

    @Nested
    @DisplayName("删除翻译历史")
    class DeleteHistoryTests {

        @Test
        void 删除历史成功() throws Exception {
            setupSecurityContext();
            when(translationTaskService.deleteHistory("history-001", 1L)).thenReturn(true);

            mockMvc.perform(delete("/v1/translate/history/history-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 记录不存在返回错误() throws Exception {
            setupSecurityContext();
            when(translationTaskService.deleteHistory("history-999", 1L)).thenReturn(false);

            mockMvc.perform(delete("/v1/translate/history/history-999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("记录不存在"));
        }
    }

    @Nested
    @DisplayName("获取翻译结果")
    class GetTranslationResultTests {

        @Test
        void 获取翻译结果成功() throws Exception {
            TranslationResultResponse result = new TranslationResultResponse();
            result.setTaskId("task-001");
            result.setStatus("completed");
            result.setTranslatedText("翻译结果");
            result.setSourceLang("en");
            result.setTargetLang("zh");
            when(translationTaskService.getTranslationResult("task-001")).thenReturn(result);

            mockMvc.perform(get("/v1/translate/task/task-001/result"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.taskId").value("task-001"))
                    .andExpect(jsonPath("$.data.translatedText").value("翻译结果"))
                    .andExpect(jsonPath("$.data.sourceLang").value("en"))
                    .andExpect(jsonPath("$.data.targetLang").value("zh"));
        }

        @Test
        void 翻译结果不可用返回错误() throws Exception {
            when(translationTaskService.getTranslationResult("not-found")).thenReturn(null);

            mockMvc.perform(get("/v1/translate/task/not-found/result"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("任务不存在或结果不可用"));
        }
    }

    @Nested
    @DisplayName("下载翻译文件")
    class DownloadTranslationTests {

        @Test
        void 下载路径不存在返回404() throws Exception {
            setupSecurityContext();
            when(translationTaskService.getDownloadPath("task-999", 1L)).thenReturn(null);

            mockMvc.perform(get("/v1/translate/task/task-999/download"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("文档流式翻译（上传文件）")
    class StreamDocumentTranslateTests {

        @Test
        void 上传文件流式翻译成功() throws Exception {
            setupSecurityContext();
            SseEmitter emitter = new SseEmitter();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".getBytes());

            when(translationTaskService.streamTranslateDocument(any(), eq("en"), eq("zh"), eq("fast")))
                    .thenReturn(emitter);

            mockMvc.perform(multipart("/v1/translate/document/stream")
                            .file(file)
                            .param("sourceLang", "en")
                            .param("targetLang", "zh")
                            .param("mode", "fast"))
                    .andExpect(status().isOk());
        }

        @Test
        void 使用默认参数流式翻译() throws Exception {
            setupSecurityContext();
            SseEmitter emitter = new SseEmitter();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "Hello".getBytes());

            when(translationTaskService.streamTranslateDocument(any(), eq("auto"), eq("zh"), eq("fast")))
                    .thenReturn(emitter);

            mockMvc.perform(multipart("/v1/translate/document/stream")
                            .file(file))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文档流式翻译（基于已有文档）")
    class StreamDocumentTranslateByIdTests {

        @Test
        void 基于已有文档流式翻译成功() throws Exception {
            setupSecurityContext();
            SseEmitter emitter = new SseEmitter();
            when(translationTaskService.streamTranslateDocumentById(1L, "zh", "fast"))
                    .thenReturn(emitter);

            mockMvc.perform(post("/v1/translate/document/stream/1")
                            .param("targetLang", "zh")
                            .param("mode", "fast"))
                    .andExpect(status().isOk());
        }

        @Test
        void 使用默认参数流式翻译() throws Exception {
            setupSecurityContext();
            SseEmitter emitter = new SseEmitter();
            when(translationTaskService.streamTranslateDocumentById(42L, "zh", "fast"))
                    .thenReturn(emitter);

            mockMvc.perform(post("/v1/translate/document/stream/42"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务管理")
    class TaskManagementTests {

        @Test
        void 查询任务状态成功() throws Exception {
            TranslationTask task = new TranslationTask();
            task.setTaskId("task-001");
            task.setStatus("completed");
            when(translationTaskService.getTaskByTaskId("task-001")).thenReturn(task);

            TaskStatusResponse statusResp = new TaskStatusResponse();
            statusResp.setTaskId("task-001");
            statusResp.setStatus("completed");
            when(translationTaskService.toTaskStatusResponse(any())).thenReturn(statusResp);

            mockMvc.perform(get("/v1/translate/task/task-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.taskId").value("task-001"));
        }

        @Test
        void 任务不存在返回404() throws Exception {
            when(translationTaskService.getTaskByTaskId("not-found")).thenReturn(null);

            mockMvc.perform(get("/v1/translate/task/not-found"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("B0001"));
        }
    }

    @Nested
    @DisplayName("RAG 翻译记忆查询")
    class RagTranslationTests {

        @Test
        void RAG查询成功() throws Exception {
            RagTranslationResponse response = new RagTranslationResponse();
            response.setTranslation("参考译文");
            response.setDirectHit(false);
            response.setSimilarity(0.85);
            when(ragTranslationService.searchSimilar(any(), any(), any())).thenReturn(response);

            mockMvc.perform(post("/v1/translate/rag")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Hello World\",\"targetLang\":\"zh\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.translation").value("参考译文"))
                    .andExpect(jsonPath("$.data.similarity").value(0.85));
        }
    }
}
