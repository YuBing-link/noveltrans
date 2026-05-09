package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.port.out.payment.CustomerInfo;
import com.yumu.noveltranslator.port.out.payment.PaymentSessionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionUpdateRequest;

import java.util.Map;

public interface PaymentPort {
    String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl, Map<String, String> metadata);
    String createBillingPortalSession(String customerId, String returnUrl);
    PaymentSessionInfo retrieveCheckoutSession(String sessionId);
    SubscriptionInfo retrieveSubscription(String subscriptionId);
    SubscriptionInfo updateSubscription(String subscriptionId, SubscriptionUpdateRequest request);
    CustomerInfo createCustomer(String email);
}
