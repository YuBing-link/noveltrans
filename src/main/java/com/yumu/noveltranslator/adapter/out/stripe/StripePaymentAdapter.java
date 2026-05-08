package com.yumu.noveltranslator.adapter.out.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.yumu.noveltranslator.port.out.PaymentPort;
import com.yumu.noveltranslator.port.out.payment.CustomerInfo;
import com.yumu.noveltranslator.port.out.payment.PaymentSessionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    @Override
    public PaymentSessionInfo retrieveCheckoutSession(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId);
            return new PaymentSessionInfo(
                session.getId(),
                session.getPaymentStatus(),
                session.getSubscription(),
                session.getMetadata()
            );
        } catch (StripeException e) {
            log.error("Failed to retrieve checkout session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("获取支付会话失败", e);
        }
    }

    @Override
    public SubscriptionInfo retrieveSubscription(String subscriptionId) {
        try {
            Subscription sub = Subscription.retrieve(subscriptionId);
            String firstItemId = Optional.ofNullable(sub.getItems())
                .map(items -> items.getData())
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getId())
                .orElse(null);
            String priceId = Optional.ofNullable(sub.getItems())
                .map(items -> items.getData())
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .map(SubscriptionItem::getPrice)
                .map(price -> price.getId())
                .orElse(null);
            return new SubscriptionInfo(
                sub.getId(),
                sub.getStatus(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getCancelAtPeriodEnd(),
                firstItemId,
                priceId,
                sub.getCanceledAt()
            );
        } catch (StripeException e) {
            log.error("Failed to retrieve subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("获取订阅信息失败", e);
        }
    }

    @Override
    public SubscriptionInfo updateSubscription(String subscriptionId, SubscriptionUpdateRequest request) {
        try {
            Subscription stripeSub = Subscription.retrieve(subscriptionId);

            SubscriptionUpdateParams.Builder builder = SubscriptionUpdateParams.builder();

            if (request.removeItemId() != null) {
                builder.addItem(SubscriptionUpdateParams.Item.builder()
                    .setId(request.removeItemId())
                    .setDeleted(true)
                    .build());
            }

            if (request.newPriceId() != null) {
                builder.addItem(SubscriptionUpdateParams.Item.builder()
                    .setPrice(request.newPriceId())
                    .setQuantity(1L)
                    .build());
            }

            if (request.cancelAtPeriodEnd() != null) {
                builder.setCancelAtPeriodEnd(request.cancelAtPeriodEnd());
            }

            builder.setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE);

            Subscription updated = stripeSub.update(builder.build());

            String firstItemId = Optional.ofNullable(updated.getItems())
                .map(items -> items.getData())
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getId())
                .orElse(null);
            String priceId = Optional.ofNullable(updated.getItems())
                .map(items -> items.getData())
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .map(SubscriptionItem::getPrice)
                .map(price -> price.getId())
                .orElse(null);

            return new SubscriptionInfo(
                updated.getId(),
                updated.getStatus(),
                updated.getCurrentPeriodStart(),
                updated.getCurrentPeriodEnd(),
                updated.getCancelAtPeriodEnd(),
                firstItemId,
                priceId,
                updated.getCanceledAt()
            );
        } catch (StripeException e) {
            log.error("Failed to update subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("更新订阅失败", e);
        }
    }

    @Override
    public CustomerInfo createCustomer(String email) {
        try {
            com.stripe.param.CustomerCreateParams params =
                com.stripe.param.CustomerCreateParams.builder()
                    .setEmail(email)
                    .build();
            com.stripe.model.Customer customer = com.stripe.model.Customer.create(params);
            return new CustomerInfo(customer.getId(), customer.getEmail());
        } catch (StripeException e) {
            log.error("Failed to create Stripe customer for email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("创建 Stripe 客户失败", e);
        }
    }
}
