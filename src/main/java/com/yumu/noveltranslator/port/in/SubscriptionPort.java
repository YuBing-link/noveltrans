package com.yumu.noveltranslator.port.in;

import com.stripe.model.Event;
import com.yumu.noveltranslator.port.dto.subscription.*;

public interface SubscriptionPort {
    PaymentVerificationResponse verifyCheckoutSession(String sessionId, Long userId);
    CheckoutSessionResponse createCheckoutSession(Long userId, CheckoutSessionRequest request);
    SubscriptionStatusResponse getSubscriptionStatus(Long userId);
    SubscriptionStatusResponse cancelSubscription(Long userId);
    PortalSessionResponse createPortalSession(Long userId);

    // Webhook handlers
    void handleCheckoutSessionCompleted(Event event);
    void handleSubscriptionUpdated(Event event);
    void handleSubscriptionDeleted(Event event);
    void handleSubscriptionResumed(Event event);
    void handleInvoicePaymentFailed(Event event);
    void handleInvoicePaymentSucceeded(Event event);
}
