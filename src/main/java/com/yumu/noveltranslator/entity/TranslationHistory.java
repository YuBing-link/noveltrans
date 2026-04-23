package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 翻译历史实体
 */
@Data
@TableName(value = "translation_history", autoResultMap = true)
public class TranslationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务类型：text, document
     */
    private String type;

    /**
     * 文档 ID（文档翻译时）
     */
    private Long documentId;

    /**
     * 源语言
     */
    private String sourceLang;

    /**
     * 目标语言
     */
    private String targetLang;

    /**
     * 原文内容（文本翻译时）
     */
    private String sourceText;

    /**
     * 译文内容（文本翻译时）
     */
    private String targetText;

    /**
     * 翻译引擎
     */
    private String engine;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
