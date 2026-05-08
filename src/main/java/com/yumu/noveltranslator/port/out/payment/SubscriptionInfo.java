package com.yumu.noveltranslator.port.out.payment;

public record SubscriptionInfo(
    String id,
    String status,
    Long currentPeriodStart,
    Long currentPeriodEnd,
    Boolean cancelAtPeriodEnd,
    String firstItemId,
    String priceId,
    Long canceledAt
) {}
