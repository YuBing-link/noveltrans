package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;

/**
 * 带占位符的翻译请求
 */
@Data
public class TranslateWithPlaceholdersRequest {
    /** 包含占位符的待翻译文本 */
    private String text;
    /** 目标语言 */
    private String targetLang;
    /** 翻译引擎 */
    private String engine = "openai";
    /** 是否启用引擎降级 */
    private boolean fallback = true;
}
