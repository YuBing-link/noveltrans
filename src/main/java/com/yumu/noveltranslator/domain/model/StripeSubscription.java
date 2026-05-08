package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StripeSubscription {
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
    private Long lastEventCreated;
    /**
     * 记录非 Webhook 来源的操作（如手动升级、取消），避免污染 lastWebhookEventId。
     * 格式："upgrade_{timestamp}" / "manual_cancel_{timestamp}"
     */
    private String lastOperationSource;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
