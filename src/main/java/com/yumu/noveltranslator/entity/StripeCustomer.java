package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Stripe 客户映射表
 */
@Data
@TableName("stripe_customer")
public class StripeCustomer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String stripeCustomerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
