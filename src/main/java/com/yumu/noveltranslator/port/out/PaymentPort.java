package com.yumu.noveltranslator.port.out;

public interface PaymentPort {
    String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl);
    String createBillingPortalSession(String customerId, String returnUrl);
}
