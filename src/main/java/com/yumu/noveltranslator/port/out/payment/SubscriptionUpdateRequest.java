package com.yumu.noveltranslator.port.out.payment;

public record SubscriptionUpdateRequest(
    String removeItemId,
    String newPriceId,
    Boolean cancelAtPeriodEnd
) {}
