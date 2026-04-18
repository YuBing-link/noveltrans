package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 实体翻译响应
 */
@Data
public class EntityTranslationResponse {
    private int code;
    private Map<String, String> translations;
}
