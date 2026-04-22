package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节实体映射表
 * 持久化每章的原文实体 → 译文实体映射，服务重启不丢失
 */
@Data
@TableName("chapter_entity_map")
public class ChapterEntityMap {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private Long projectId;

    /**
     * 原文实体（如人名、地名、技能名等）
     */
    private String sourceEntity;

    /**
     * 译文实体
     */
    private String targetEntity;

    /**
     * 实体类型：person/place/skill/organization/other
     */
    private String entityType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
