package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * API Key 实体
 */
@Data
@TableName("api_keys")
public class ApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String apiKey;

    private String name;

    @TableField("is_active")
    private Boolean active;

    private LocalDateTime lastUsedAt;

    private Long totalUsage;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private LocalDateTime createdAt;
}
