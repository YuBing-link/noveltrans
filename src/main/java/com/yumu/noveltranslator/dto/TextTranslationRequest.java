package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文本翻译请求
 */
@Data
public class TextTranslationRequest {
    /**
     * 待翻译文本
     */
    @NotBlank(message = "翻译文本不能为空")
    private String text;

    /**
     * 源语言代码，auto 表示自动检测
     */
    private String sourceLang = "auto";

    /**
     * 目标语言代码
     */
    private String targetLang;

    /**
     * 翻译模式：novel(小说), literal(直译), free(意译)
     */
    private String mode = "novel";

    /**
     * 翻译引擎
     */
    private String engine;
}
