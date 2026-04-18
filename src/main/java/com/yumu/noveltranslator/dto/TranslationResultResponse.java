package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 翻译结果响应
 */
@Data
public class TranslationResultResponse {
    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 任务状态
     */
    private String status;
    /**
     * 翻译后的文本（文本翻译）
     */
    private String translatedText;
    /**
     * 原文内容
     */
    private String sourceContent;
    /**
     * 翻译后的文件路径（文档翻译）
     */
    private String translatedFilePath;
    /**
     * 源语言
     */
    private String sourceLang;
    /**
     * 目标语言
     */
    private String targetLang;
    /**
     * 完成时间
     */
    private String completedTime;
}
