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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
