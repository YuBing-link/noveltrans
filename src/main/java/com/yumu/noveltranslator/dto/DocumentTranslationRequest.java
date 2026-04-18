package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档翻译请求
 */
@Data
public class DocumentTranslationRequest {
    /**
     * 源语言
     */
    private String sourceLang = "auto";

    /**
     * 目标语言
     */
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;

    /**
     * 翻译模式：novel, literal, free
     */
    private String mode = "novel";

    /**
     * 翻译引擎：ai, google, deepl
     */
    private String engine = "google";
}
