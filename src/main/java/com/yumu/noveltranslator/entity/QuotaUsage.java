package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日配额使用记录
 */
@Data
@TableName("quota_usage")
public class QuotaUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate usageDate;

    private Long charactersUsed;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
