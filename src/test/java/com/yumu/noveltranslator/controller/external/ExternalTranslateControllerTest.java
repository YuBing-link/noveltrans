package com.yumu.noveltranslator.controller.external;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // Helper to create JSON with snake_case keys matching @JsonProperty annotations
    private static String translateJson(String text, String targetLang, String sourceLang, String engine, String mode) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"text\":\"").append(text).append("\"");
        sb.append(",\"target_lang\":\"").append(targetLang).append("\"");
        if (sourceLang != null) sb.append(",\"source_lang\":\"").append(sourceLang).append("\"");
        if (engine != null) sb.append(",\"engine\":\"").append(engine).append("\"");
        if (mode != null) sb.append(",\"mode\":\"").append(mode).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String batchJson(String[] texts, String targetLang, String sourceLang, String engine, String mode) {
        StringBuilder sb = new StringBuilder("{\"texts\":[");
        for (int i = 0; i < texts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(texts[i]).append("\"");
        }
        sb.append("]");
        sb.append(",\"target_lang\":\"").append(targetLang).append("\"");
        if (sourceLang != null) sb.append(",\"source_lang\":\"").append(sourceLang).append("\"");
        if (engine != null) sb.append(",\"engine\":\"").append(engine).append("\"");
        if (mode != null) sb.append(",\"mode\":\"").append(mode).append("\"");
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // POST /v1/external/translate - Single text translation
    // =========================================================================

    @Nested
    @DisplayName("POST /translate - single text translation")
    class TranslateTests {

        @Test
        @DisplayName("translate successfully with minimal fields")
        void translateSuccessMinimal() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "Hello translated"));

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("Hello", "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.translatedText").value("Hello translated"));
        }

        @Test
        @DisplayName("translate successfully with all fields")
        void translateSuccessAllFields() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "deepl", "Uebersetzt"));

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("Hello World", "de", "en", "deepl", "expert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.translatedText").value("Uebersetzt"))
                .andExpect(jsonPath("$.data.engine").value("deepl"));
        }

        @Test
        @DisplayName("text is empty/blank returns error")
        void translateTextEmpty() throws Exception {
            setupSecurityContext();

            // Empty string - @NotBlank validation rejects it with 400
            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("", "zh", null, null, null)))
                .andExpect(status().isBadRequest());

            // Blank string (spaces only) - @NotBlank also rejects whitespace-only with 400
            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("   ", "zh", null, null, null)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("target language is empty returns 400 validation error")
        void translateTargetLangEmpty() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("Hello", "", null, null, null)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("target language is null returns 400 validation error")
        void translateTargetLangNull() throws Exception {
            setupSecurityContext();

            // JSON without target_lang field at all
            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("text exceeds maxCharsPerRequest returns error")
        void translateTextTooLong() throws Exception {
            setupSecurityContext();

            String longText = "A".repeat(5001);
            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson(longText, "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("文本超过限制（最大 5000 字符）"));
        }

        @Test
        @DisplayName("translation service exception returns error")
        void translateServiceException() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenThrow(new RuntimeException("Engine connection failed"));

            mockMvc.perform(post("/v1/external/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(translateJson("Hello", "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("翻译失败：Engine connection failed"));
        }
    }

    // =========================================================================
    // POST /v1/external/batch - Batch text translation
    // =========================================================================

    @Nested
    @DisplayName("POST /batch - batch text translation")
    class BatchTranslateTests {

        @Test
        @DisplayName("batch translate successfully with multiple texts")
        void batchTranslateSuccess() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "translated"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(new String[]{"Hello", "World"}, "zh", "en", "google", "fast")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].translatedText").value("translated"))
                .andExpect(jsonPath("$.data[1].translatedText").value("translated"));
        }

        @Test
        @DisplayName("batch translate single item")
        void batchTranslateSingleItem() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "result"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(new String[]{"Single"}, "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].translatedText").value("result"));
        }

        @Test
        @DisplayName("empty texts list returns error")
        void batchTranslateEmptyList() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"texts\":[],\"target_lang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("文本列表不能为空"));
        }

        @Test
        @DisplayName("texts list is null returns 400 validation error")
        void batchTranslateNullList() throws Exception {
            setupSecurityContext();

            // JSON without texts field
            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"target_lang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("more than 50 items returns error")
        void batchTranslateOver50() throws Exception {
            setupSecurityContext();

            String[] texts = new String[51];
            for (int i = 0; i < 51; i++) {
                texts[i] = "text" + i;
            }

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(texts, "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("批量翻译最多支持 50 条文本"));
        }

        @Test
        @DisplayName("exactly 50 items is allowed")
        void batchTranslateExactly50() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "google", "ok"));

            String[] texts = new String[50];
            for (int i = 0; i < 50; i++) {
                texts[i] = "text" + i;
            }

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(texts, "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(50));
        }

        @Test
        @DisplayName("some items fail translation, error field populated")
        void batchTranslatePartialFailure() throws Exception {
            setupSecurityContext();
            // All calls throw - each item gets an error field
            when(translationService.selectionTranslate(any()))
                .thenThrow(new RuntimeException("Engine timeout"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(new String[]{"Hello", "World"}, "zh", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].error").value("Engine timeout"))
                .andExpect(jsonPath("$.data[1].error").value("Engine timeout"));
        }

        @Test
        @DisplayName("batch translate with expert mode")
        void batchTranslateWithMode() throws Exception {
            setupSecurityContext();
            when(translationService.selectionTranslate(any()))
                .thenReturn(new SelectionTranslateResponse(true, "openai", "AI result"));

            mockMvc.perform(post("/v1/external/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchJson(new String[]{"Complex sentence"}, "de", "en", "openai", "expert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].translatedText").value("AI result"));
        }
    }

    // =========================================================================
    // GET /v1/external/models - available translation engines
    // =========================================================================

    @Nested
    @DisplayName("GET /models - available translation engines")
    class GetModelsTests {

        @Test
        @DisplayName("returns 6 models successfully")
        void getModelsReturnsSix() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(6));
        }

        @Test
        @DisplayName("first model is Google Translate")
        void getModelsFirstIsGoogle() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(jsonPath("$.data[0].id").value("google"))
                .andExpect(jsonPath("$.data[0].name").value("Google Translate"))
                .andExpect(jsonPath("$.data[0].type").value("free"));
        }

        @Test
        @DisplayName("includes API key models")
        void getModelsIncludesApiKey() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(jsonPath("$.data[?(@.id=='deepl')].name").value("DeepL"))
                .andExpect(jsonPath("$.data[?(@.id=='openai')].name").value("OpenAI"))
                .andExpect(jsonPath("$.data[?(@.id=='baidu')].name").value("Baidu Translate"));
        }

        @Test
        @DisplayName("all models have modes array")
        void getModelsAllHaveModes() throws Exception {
            mockMvc.perform(get("/v1/external/models"))
                .andExpect(jsonPath("$.data[0].modes.length()").value(3))
                .andExpect(jsonPath("$.data[3].modes.length()").value(3));
        }
    }

    // =========================================================================
    // GET /v1/external/task/{taskId}/download - download translation
    // =========================================================================

    @Nested
    @DisplayName("GET /task/{taskId}/download - download translation result")
    class DownloadTranslationTests {

        @Test
        @DisplayName("task not found returns 404")
        void downloadTaskNotFound() throws Exception {
            setupSecurityContext();
            when(translationTaskService.getDownloadPath("task-nonexistent", 1L)).thenReturn(null);

            mockMvc.perform(get("/v1/external/task/task-nonexistent/download"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("download successful returns file content")
        void downloadSuccess() throws Exception {
            setupSecurityContext();
            Path tempFile = Files.createTempFile("translated-", ".txt");
            Files.writeString(tempFile, "Translated content");

            when(translationTaskService.getDownloadPath("task-001", 1L))
                .thenReturn(tempFile.toString());

            mockMvc.perform(get("/v1/external/task/task-001/download"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(tempFile)))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("translated_task-001")));

            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("file read IOException returns 500")
        void downloadFileReadError() throws Exception {
            setupSecurityContext();
            when(translationTaskService.getDownloadPath("task-002", 1L))
                .thenReturn("/nonexistent/path/to/file.txt");

            mockMvc.perform(get("/v1/external/task/task-002/download"))
                .andExpect(status().isInternalServerError());
        }
    }
}
