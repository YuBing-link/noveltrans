package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WebpageTranslateRequest {
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;

    private String sourceLang;

    private String engine; // google, deepl, openai, baidu

    private Boolean fastMode; // true=MTranServer(快速), false=DeepSeek(专家), null=后端决定

    @NotEmpty(message = "翻译文本列表不能为空")
    private List<TextItem> textRegistry;

    @Data
    public static class TextItem {
        private String id;
        private String original;
        private String context;
    }
}
