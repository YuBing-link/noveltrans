package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 翻译任务状态响应
 */
@Data
public class TaskStatusResponse {
    /**
     * 任务 ID
     */
    private String taskId;
    /**
     * 任务类型：text, document
     */
    private String type;
    /**
     * 任务状态：pending, processing, translating, completed, failed
     */
    private String status;
    /**
     * 进度（0-100）
     */
    private Integer progress;
    /**
     * 源语言
     */
    private String sourceLang;
    /**
     * 目标语言
     */
    private String targetLang;
    /**
     * 创建时间
     */
    private String createTime;
    /**
     * 完成时间
     */
    private String completedTime;
    /**
     * 错误信息
     */
    private String errorMessage;
}
