package com.yumu.noveltranslator.controller.shared;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
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

    // 文本翻译端点 (/v1/translate/text) 在 PluginTranslateController 中定义
    // 该控制器的测试请参见 PluginTranslateControllerTest

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
