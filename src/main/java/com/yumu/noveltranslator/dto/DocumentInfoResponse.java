package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 文档信息响应
 */
@Data
public class DocumentInfoResponse {
    /**
     * 文档 ID
     */
    private Long id;
    /**
     * 文档名称
     */
    private String name;
    /**
     * 文档类型
     */
    private String fileType;
    /**
     * 文档大小
     */
    private Long fileSize;
    /**
     * 源语言
     */
    private String sourceLang;
    /**
     * 目标语言
     */
    private String targetLang;
    /**
     * 翻译状态
     */
    private String status;
    /**
     * 进度
     */
    private Integer progress;
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
