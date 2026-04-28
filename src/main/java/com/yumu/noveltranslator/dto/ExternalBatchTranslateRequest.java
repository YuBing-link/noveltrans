package com.yumu.noveltranslator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 外部 API 批量翻译请求
 */
@Data
public class ExternalBatchTranslateRequest {
    @NotBlank(message = "目标语言不能为空")
    @JsonProperty("target_lang")
    private String targetLang;
    @JsonProperty("source_lang")
    private String sourceLang;
    private String engine;
    /** 翻译模式：fast（快速）/ expert（专家）/ team（多Agent），默认 fast */
    @JsonProperty("mode")
    private String mode;
    private List<@NotBlank(message = "文本不能为空") String> texts;
}
