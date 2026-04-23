package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 翻译任务实体
 */
@Data
@TableName(value = "translation_task", autoResultMap = true)
public class TranslationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务 ID（UUID）
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 用户 ID
     */
    private Long userId;

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
     * 翻译模式：novel, literal, free
     */
    private String mode;

    /**
     * 翻译引擎：ai, google, deepl
     */
    private String engine;

    /**
     * 任务状态：见 {@link com.yumu.noveltranslator.enums.TranslationStatus}
     */
    private String status;

    /**
     * 进度（0-100）
     */
    private Integer progress;

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
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
