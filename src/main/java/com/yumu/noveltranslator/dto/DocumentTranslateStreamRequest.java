package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 文档流式翻译请求
 */
@Data
public class DocumentTranslateStreamRequest {
    private String sourceLang = "auto";
    private String targetLang = "zh";
    private String mode = "fast";
}
