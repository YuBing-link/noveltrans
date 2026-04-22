package com.yumu.noveltranslator.controller.external;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.QuotaService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalTranslateControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private TranslationService translationService;

    @org.mockito.Mock
    private DocumentService documentService;

    @org.mockito.Mock
    private TranslationTaskService translationTaskService;

    @org.mockito.Mock
    private QuotaService quotaService;

    private ExternalTranslateController controller;

    @BeforeEach
    void setUp() {
        controller = new ExternalTranslateController(translationService, documentService, translationTaskService, quotaService);
        ReflectionTestUtils.setField(controller, "maxCharsPerRequest", 5000);
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

    @Nested
    @DisplayName("文本翻译")
    class TranslateTests {

        @Test
        void 文本翻译成功() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "翻译结果"));

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Hello World\",\"targetLang\":\"zh\",\"sourceLang\":\"en\",\"engine\":\"google\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.translatedText").value("翻译结果"));
        }

        @Test
        void 文本为空返回400() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"\",\"targetLang\":\"zh\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 目标语言为空返回400() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 翻译服务异常返回错误() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any())).thenThrow(new RuntimeException("Engine error"));

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Hello\",\"targetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("批量翻译")
    class BatchTranslateTests {

        @Test
        void 批量翻译成功() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "翻译结果"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":[\"Hello\",\"World\"],\"targetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        void 文本列表为空返回错误() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":[],\"targetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void 批量超过50条返回错误() throws Exception {
            setupSecurityContext();
            StringBuilder texts = new StringBuilder("[");
            for (int i = 0; i < 51; i++) {
                if (i > 0) texts.append(",");
                texts.append("\"text").append(i).append("\"");
            }
            texts.append("]");

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":" + texts + ",\"targetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("批量翻译最多支持 50 条文本"));
        }

        @Test
        void 部分翻译失败返回错误项() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenThrow(new RuntimeException("Engine error"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":[\"Hello\"],\"targetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].error").exists());
        }
    }

    @Nested
    @DisplayName("获取翻译引擎列表")
    class GetModelsTests {

        @Test
        void 获取引擎列表成功() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(6));
        }

        @Test
        void 返回Google引擎() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(jsonPath("$.data[0].id").value("google"));
        }
    }

    @Nested
    @DisplayName("下载翻译结果")
    class DownloadTranslationTests {

        @Test
        void 任务不存在返回404() throws Exception {
            setupSecurityContext();
            when(translationTaskService.getDownloadPath("task-001", 1L)).thenReturn(null);

            mockMvc.perform(get("/v1/external/task/task-001/download"))
                .andExpect(status().isNotFound());
        }
    }
}
