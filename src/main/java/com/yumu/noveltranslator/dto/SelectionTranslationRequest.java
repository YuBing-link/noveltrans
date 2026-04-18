package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for selection translation endpoint
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionTranslationRequest {
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
    private String targetLang = "zh";

    /**
     * 翻译引擎
     */
    private String engine;

    /**
     * 上下文
     */
    private String context;

    /**
     * 翻译模式: fast/expert/team
     */
    private String mode = "fast";

}
