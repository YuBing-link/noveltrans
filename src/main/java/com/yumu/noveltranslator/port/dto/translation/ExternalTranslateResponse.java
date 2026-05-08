package com.yumu.noveltranslator.port.dto.translation;

import lombok.Data;

/**
 * 外部 API 翻译响应
 */
@Data
public class ExternalTranslateResponse {
    private String translatedText;
    private String sourceLang;
    private String targetLang;
    private String engine;
    private Integer usage;
    private String error;
}
