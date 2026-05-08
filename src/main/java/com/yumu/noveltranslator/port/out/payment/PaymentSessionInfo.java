package com.yumu.noveltranslator.port.out.payment;

import java.util.Map;

public record PaymentSessionInfo(
    String id,
    String paymentStatus,
    String subscriptionId,
    Map<String, String> metadata
) {}
