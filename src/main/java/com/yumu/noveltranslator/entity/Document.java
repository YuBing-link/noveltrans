package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档翻译实体
 */
@Data
@TableName(value = "document", autoResultMap = true)
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文档路径
     */
    private String path;

    /**
     * 源语言
     */
    private String sourceLang;

    /**
     * 目标语言
     */
    private String targetLang;

    /**
     * 文档类型：txt, epub, docx
     */
    private String fileType;

    /**
     * 文档大小（字节）
     */
    private Long fileSize;

    /**
     * 关联的翻译任务 ID
     */
    private String taskId;

    /**
     * 翻译状态：见 {@link com.yumu.noveltranslator.enums.TranslationStatus}
     */
    private String status;

    /**
     * 翻译模式：novel, literal, free
     */
    private String mode;

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
