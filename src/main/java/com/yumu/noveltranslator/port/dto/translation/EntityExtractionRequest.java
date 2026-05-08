package com.yumu.noveltranslator.port.dto.translation;

import lombok.Data;

/**
 * 实体提取请求
 */
@Data
public class EntityExtractionRequest {
    private String text;
    private String sourceLang = "zh";
    private String targetLang = "en";
}
