package com.yumu.noveltranslator.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe Webhook 接收端点
 * 接收 Stripe 的订阅状态变更通知，异步处理数据库更新。
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final SubscriptionService subscriptionService;
    private final com.yumu.noveltranslator.properties.StripeProperties stripeProperties;
    private final UserMapper userMapper;

    @PostMapping("/stripe")
    public String handleStripeWebhook(HttpServletRequest request, @RequestBody String payload) {
        String sigHeader = request.getHeader("Stripe-Signature");

        // 1. 验证签名
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed", e);
            throw new RuntimeException("Invalid webhook signature", e);
        }

        return processEvent(event);
    }

    /**
     * 处理 Stripe 事件（提取为独立方法以便测试）
     * 包可见性，仅供同包测试使用
     */
    String processEvent(Event event) {
        log.info("Received Stripe webhook event: {}", event.getType());

        // 1. 获取 userId 并设置租户上下文
        Long userId = extractUserId(event);
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null && user.getTenantId() != null) {
                TenantContext.setTenantId(user.getTenantId());
            }
        }

        try {
            // 2. 根据事件类型分发处理
            dispatchEvent(event);
            return "{}";
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 根据事件类型分发到对应的处理方法
     * 包可见性，仅供同包测试使用
     */
    void dispatchEvent(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" ->
                subscriptionService.handleCheckoutSessionCompleted(event);

            case "customer.subscription.created" -> {
                // 忽略，避免与 checkout.session.completed 重复处理
                log.info("Ignoring customer.subscription.created event (handled by checkout.session.completed)");
            }

            case "customer.subscription.updated" ->
                subscriptionService.handleSubscriptionUpdated(event);

            case "customer.subscription.deleted" ->
                subscriptionService.handleSubscriptionDeleted(event);

            case "customer.subscription.resumed" ->
                subscriptionService.handleSubscriptionResumed(event);

            case "invoice.payment_succeeded" -> {
                // 仅记录，不改变业务状态
                log.info("invoice.payment_succeeded: {}", event.getId());
            }

            case "invoice.payment_failed" ->
                subscriptionService.handleInvoicePaymentFailed(event);

            default ->
                log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    /**
     * 从事件中提取 userId
     */
    private Long extractUserId(Event event) {
        // 优先从 subscription/session 对象的 metadata 获取
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = deserializer.getObject().orElse(null);
            if (obj == null) {
                obj = deserializer.deserializeUnsafe();
            }
            if (obj instanceof com.stripe.model.Subscription sub) {
                if (sub.getMetadata() != null && sub.getMetadata().containsKey("userId")) {
                    return Long.parseLong(sub.getMetadata().get("userId"));
                }
            } else if (obj instanceof com.stripe.model.checkout.Session session) {
                if (session.getMetadata() != null && session.getMetadata().containsKey("userId")) {
                    return Long.parseLong(session.getMetadata().get("userId"));
                }
            } else if (obj instanceof com.stripe.model.Invoice invoice) {
                if (invoice.getMetadata() != null && invoice.getMetadata().containsKey("userId")) {
                    return Long.parseLong(invoice.getMetadata().get("userId"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract userId from event object", e);
        }

        return null;
    }
}
