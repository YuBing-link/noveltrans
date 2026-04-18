package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 翻译记忆实体
 */
@Data
@TableName(value = "translation_memory", autoResultMap = true)
public class TranslationMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long projectId;

    private String sourceLang;

    private String targetLang;

    private String sourceText;

    private String targetText;

    /**
     * 向量嵌入（JSON 数组存储 float[]）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Float> embedding;

    private Integer usageCount;

    private String sourceEngine;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
