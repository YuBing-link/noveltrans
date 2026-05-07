package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.dto.subscription.*;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;

public interface SubscriptionPort {
    PaymentVerificationResponse verifyCheckoutSession(String sessionId, Long userId);
    CheckoutSessionResponse createCheckoutSession(Long userId, CheckoutSessionRequest request);
    SubscriptionStatusResponse getSubscriptionStatus(Long userId);
    SubscriptionStatusResponse cancelSubscription(Long userId);
    PortalSessionResponse createPortalSession(Long userId);
}
