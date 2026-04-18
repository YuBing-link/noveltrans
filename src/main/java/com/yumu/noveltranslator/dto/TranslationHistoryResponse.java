package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 翻译历史响应
 */
@Data
public class TranslationHistoryResponse {
    /**
     * 历史记录 ID
     */
    private Long id;
    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 任务类型：text, document
     */
    private String type;
    /**
     * 源语言
     */
    private String sourceLang;
    /**
     * 目标语言
     */
    private String targetLang;
    /**
     * 原文（截断）
     */
    private String sourceTextPreview;
    /**
     * 译文（截断）
     */
    private String targetTextPreview;
    /**
     * 创建时间
     */
    private String createTime;
}
