package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户档位变更历史
 */
@Data
@TableName("user_plan_history")
public class UserPlanHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String oldPlan;

    private String newPlan;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private LocalDateTime changedAt;

    private String note;
}
