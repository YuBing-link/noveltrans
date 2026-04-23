package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户偏好设置实体
 */
@Data
@TableName("user_preferences")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String defaultEngine;

    private String defaultTargetLang;

    private Boolean enableGlossary;

    private Long defaultGlossaryId;

    private Boolean enableCache;

    private Boolean autoTranslateSelection;

    private Integer fontSize;

    private String themeMode;

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
