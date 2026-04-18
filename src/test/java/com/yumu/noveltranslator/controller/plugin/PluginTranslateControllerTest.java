package com.yumu.noveltranslator.controller.plugin;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.service.TranslationService;
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
class PluginTranslateControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TranslationService translationService;

    private PluginTranslateController controller;

    @BeforeEach
    void setUp() {
        controller = new PluginTranslateController(translationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("选中文本翻译（公开接口）")
    class SelectionTranslationTests {

        @Test
        void 选中文本翻译成功() throws Exception {
            when(translationService.selectionTranslate(any()))
                    .thenReturn(new SelectionTranslateResponse(true, "google", "翻译结果"));

            mockMvc.perform(post("/v1/translate/selection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"Hello\",\"context\":\"Hello\",\"sourceLang\":\"en\",\"targetLang\":\"zh\",\"engine\":\"google\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.translation").value("翻译结果"));
        }

        @Test
        void 空内容返回失败() throws Exception {
            mockMvc.perform(post("/v1/translate/selection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"\",\"context\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("阅读器翻译（公开接口）")
    class ReaderTranslationTests {

        @Test
        void 阅读器翻译成功() throws Exception {
            when(translationService.readerTranslate(any()))
                    .thenReturn(new ReaderTranslateResponse(true, "google", "<p>翻译内容</p>"));

            mockMvc.perform(post("/v1/translate/reader")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"<p>Hello</p>\",\"sourceLang\":\"en\",\"targetLang\":\"zh\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.translatedContent").value("<p>翻译内容</p>"));
        }

        @Test
        void 空内容返回失败() throws Exception {
            mockMvc.perform(post("/v1/translate/reader")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
