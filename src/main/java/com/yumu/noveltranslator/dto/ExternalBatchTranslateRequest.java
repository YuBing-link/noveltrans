package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 外部 API 批量翻译请求
 */
@Data
public class ExternalBatchTranslateRequest {
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;
    private String sourceLang;
    private String engine;
    private List<@NotBlank(message = "文本不能为空") String> texts;
}
