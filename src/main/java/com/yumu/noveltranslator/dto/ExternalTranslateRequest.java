package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 外部 API 翻译请求
 */
@Data
public class ExternalTranslateRequest {
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;
    private String sourceLang;
    @NotBlank(message = "文本不能为空")
    private String text;
    private String engine;
}
