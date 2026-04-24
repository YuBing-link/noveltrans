package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Stripe 订阅记录表
 */
@Data
@TableName("stripe_subscription")
public class StripeSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String stripeCustomerId;

    private String stripeSubscriptionId;

    private String plan;

    private String status;

    private String stripePriceId;

    private String billingCycle;

    private LocalDateTime currentPeriodStart;

    private LocalDateTime currentPeriodEnd;

    private Boolean cancelAtPeriodEnd;

    private LocalDateTime canceledAt;

    private String lastWebhookEventId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
