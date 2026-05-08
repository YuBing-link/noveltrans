package com.yumu.noveltranslator.adapter.out.persistence.entity;

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

    /** Stripe 事件时间戳（秒级 epoch），用于防止 out-of-order 事件覆写 */
    private Long lastEventCreated;

    /** 非 Webhook 操作来源（upgrade/cancel 等），避免污染 lastWebhookEventId */
    private String lastOperationSource;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
