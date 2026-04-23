package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 维护的术语表实体
 * 每个小说项目独立维护，AI 翻译章节时自动提取术语
 * 优先级：用户术语表 > AI 术语表（已确认）
 */
@Data
@TableName("ai_glossary")
public class AiGlossary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目 ID（小说）
     */
    private Long projectId;

    /**
     * 原文术语
     */
    private String sourceWord;

    /**
     * 译文术语
     */
    private String targetWord;

    /**
     * 提取上下文（摘录）
     */
    private String context;

    /**
     * 实体类型：person/place/skill/organization/other
     */
    private String entityType;

    /**
     * 首次出现的章节 ID
     */
    private Long chapterId;

    /**
     * AI 提取置信度
     */
    private Double confidence;

    /**
     * 状态：pending（待确认）/ confirmed（已确认）/ rejected（已拒绝）
     */
    private String status;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
