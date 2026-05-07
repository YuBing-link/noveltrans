package com.yumu.noveltranslator.adapter.out.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.yumu.noveltranslator.port.out.PaymentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbound port adapter for Stripe payment operations.
 * Wraps Stripe SDK calls, implementing the PaymentPort driven interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripePaymentAdapter implements PaymentPort {

    @Override
    public String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .build();
            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create checkout session for customer {}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("创建支付会话失败", e);
        }
    }

    @Override
    public String createBillingPortalSession(String customerId, String returnUrl) {
        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl)
                    .build();
            com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(params);
            return portalSession.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create billing portal session for customer {}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("创建账单管理链接失败", e);
        }
    }
}
