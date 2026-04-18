package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 文档翻译响应
 */
@Data
public class DocumentTranslationResponse {
    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 文档 ID
     */
    private Long documentId;
    /**
     * 文档名称
     */
    private String documentName;
    /**
     * 任务状态
     */
    private String status;
    /**
     * 消息
     */
    private String message;
}
