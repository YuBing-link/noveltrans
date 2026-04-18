package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 文本翻译响应
 */
@Data
public class TextTranslationResponse {
    /**
     * 翻译后的文本
     */
    private String translatedText;
    /**
     * 检测到的源语言
     */
    private String detectedLang;
    /**
     * 目标语言
     */
    private String targetLang;
    /**
     * 翻译引擎
     */
    private String engine;
    /**
     * 耗时（毫秒）
     */
    private Long costTime;
}
