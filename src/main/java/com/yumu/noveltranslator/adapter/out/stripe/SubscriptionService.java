package com.yumu.noveltranslator.adapter.out.stripe;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.CustomerCreateParams;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionRequest;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionResponse;
import com.yumu.noveltranslator.port.dto.subscription.PaymentVerificationResponse;
import com.yumu.noveltranslator.port.dto.subscription.PortalSessionResponse;
import com.yumu.noveltranslator.port.dto.subscription.SubscriptionStatusResponse;
import com.yumu.noveltranslator.domain.model.StripeCustomer;
import com.yumu.noveltranslator.domain.model.StripeSubscription;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPlanHistory;
import com.yumu.noveltranslator.enums.BillingCycle;
import com.yumu.noveltranslator.enums.SubscriptionPlan;
import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.properties.StripeProperties;
import com.yumu.noveltranslator.adapter.out.redis.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Stripe 订阅支付核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService implements com.yumu.noveltranslator.port.in.SubscriptionPort {

    private final StripeProperties stripeProperties;
    private final BillingRepositoryPort billingPort;
    private final UserRepositoryPort userRepositoryPort;
    private final StringRedisTemplate stringRedisTemplate;
    private final TokenBlacklistService tokenBlacklistService;
    private final com.yumu.noveltranslator.util.JwtUtils jwtUtils;
    private final com.yumu.noveltranslator.port.out.PaymentPort paymentPort;

    // ==================== 用户端 API ====================

    /**
     * 验证 Checkout Session 支付结果（前端回调时主动查询）
     */
    public PaymentVerificationResponse verifyCheckoutSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new PaymentVerificationResponse(false, sessionId, null, null, "缺少 session_id 参数");
        }

        try {
            Session session = Session.retrieve(sessionId);

            // 验证该 session 是否属于当前用户
            String metadataUserId = session.getMetadata().get("userId");
            if (metadataUserId == null || !metadataUserId.equals(String.valueOf(userId))) {
                return new PaymentVerificationResponse(false, sessionId, null, null, "支付会话不属于当前用户");
            }

            String paymentStatus = session.getPaymentStatus();
            String plan = session.getMetadata().get("plan");

            if ("paid".equals(paymentStatus)) {
                // 检查本地是否已处理（webhook 是否已送达）
                StripeSubscription sub = billingPort.findSubscriptionByStripeId(session.getSubscription());

                if (sub != null) {
                    return new PaymentVerificationResponse(true, sessionId, plan, sub.getStatus(), "支付成功，订阅已激活");
                }
                // webhook 尚未送达，但 Stripe 已确认支付
                return new PaymentVerificationResponse(true, sessionId, plan, "pending", "支付已确认，订阅正在激活中");
            } else if ("unpaid".equals(paymentStatus)) {
                return new PaymentVerificationResponse(false, sessionId, plan, "unpaid", "支付尚未完成");
            } else if ("no_payment_required".equals(paymentStatus)) {
                return new PaymentVerificationResponse(false, sessionId, plan, "no_payment_required", "无需支付");
            }

            return new PaymentVerificationResponse(false, sessionId, plan, paymentStatus, "支付状态: " + paymentStatus);
        } catch (StripeException e) {
            log.error("Failed to verify checkout session {}: {}", sessionId, e.getMessage(), e);
            return new PaymentVerificationResponse(false, sessionId, null, null, "无法验证支付状态");
        }
    }

    /**
     * 创建订阅支付会话
     * - 无活跃订阅 → 创建新订阅 Checkout Session（走 Stripe 支付流程）
     * - 有活跃订阅 + 升级套餐 → 直接调用 Subscription.update 变更价格，Stripe 自动按比例结算差价
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(Long userId, CheckoutSessionRequest request) {
        SubscriptionPlan plan = validatePlan(request.getPlan());
        BillingCycle billingCycle = validateBillingCycle(request.getBillingCycle());
        String priceId = getPriceId(plan, billingCycle);

        // 1. 获取或创建 Stripe Customer（Stripe HTTP + DB insert，在事务外）
        StripeCustomer customer = getOrCreateCustomer(userId);

        // 2. 检查是否已有活跃订阅
        StripeSubscription existingSub = billingPort.findActiveSubscriptionByUserId(userId);

        // 3. 已有活跃订阅 → 升级现有订阅（Stripe HTTP + DB 写，拆分为事务外+内）
        if (existingSub != null) {
            return upgradeSubscriptionWithNarrowTx(existingSub, priceId, plan, billingCycle);
        }

        // 4. 无活跃订阅 → 创建新 Checkout Session（仅 Stripe HTTP，无 DB 写）
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customer.getStripeCustomerId())
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("plan", plan.getValue())
                .putMetadata("billingCycle", billingCycle.getValue())
                .setSuccessUrl(stripeProperties.getSuccessUrl())
                .setCancelUrl(stripeProperties.getCancelUrl())
                .build();

            Session session = Session.create(params);
            return new CheckoutSessionResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("Failed to create Stripe Checkout Session for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("创建支付会话失败", e);
        }
    }

    /**
     * 升级现有订阅
     */
    private CheckoutSessionResponse upgradeSubscriptionWithNarrowTx(StripeSubscription existingSub,
                                                                     String newPriceId,
                                                                     SubscriptionPlan newPlan,
                                                                     BillingCycle billingCycle) {
        // Stripe HTTP 调用
        Subscription updated;
        try {
            Subscription stripeSub = Subscription.retrieve(existingSub.getStripeSubscriptionId());

            String oldItemId = stripeSub.getItems().getData().get(0).getId();
            SubscriptionUpdateParams.Item removeItem = SubscriptionUpdateParams.Item.builder()
                .setId(oldItemId)
                .setDeleted(true)
                .build();

            SubscriptionUpdateParams.Item newItem = SubscriptionUpdateParams.Item.builder()
                .setPrice(newPriceId)
                .setQuantity(1L)
                .build();

            SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                .addItem(removeItem)
                .addItem(newItem)
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                .build();

            updated = stripeSub.update(updateParams);
        } catch (StripeException e) {
            log.error("Failed to upgrade subscription for user {}: {}", existingSub.getUserId(), e.getMessage(), e);
            throw new RuntimeException("升级订阅失败", e);
        }

        // DB 写操作在事务内
        return doUpgradeSubscription(existingSub, updated, newPriceId, newPlan, billingCycle);
    }

    private CheckoutSessionResponse doUpgradeSubscription(StripeSubscription existingSub,
                                                           Subscription updated,
                                                           String newPriceId,
                                                           SubscriptionPlan newPlan,
                                                           BillingCycle billingCycle) {
        existingSub.setPlan(newPlan.getValue());
        existingSub.setBillingCycle(billingCycle.getValue());
        existingSub.setStripePriceId(updated.getItems().getData().get(0).getPrice().getId());
        existingSub.setStatus(updated.getStatus());
        existingSub.setCancelAtPeriodEnd(updated.getCancelAtPeriodEnd());

        if (updated.getCurrentPeriodStart() != null) {
            existingSub.setCurrentPeriodStart(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(updated.getCurrentPeriodStart()), ZoneId.systemDefault()));
        }
        if (updated.getCurrentPeriodEnd() != null) {
            existingSub.setCurrentPeriodEnd(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(updated.getCurrentPeriodEnd()), ZoneId.systemDefault()));
        }

        existingSub.setLastWebhookEventId("upgrade_" + System.currentTimeMillis());
        billingPort.updateSubscription(existingSub);

        // 更新用户等级
        updateUserLevel(existingSub.getUserId(), newPlan.getValue(), "subscription_upgrade", "upgrade_" + existingSub.getStripeSubscriptionId() + "_" + newPriceId);

        log.info("Upgraded subscription {} for user {}: {} -> {}, priceId {}",
            existingSub.getStripeSubscriptionId(), existingSub.getUserId(),
            existingSub.getPlan(), newPlan, newPriceId);

        return new CheckoutSessionResponse(null);
    }

    /**
     * 获取当前订阅状态
     */
    public SubscriptionStatusResponse getSubscriptionStatus(Long userId) {
        var subscriptions = billingPort.findSubscriptionsByUserId(userId);
        StripeSubscription sub = subscriptions.isEmpty() ? null : subscriptions.get(0);

        if (sub == null) {
            return new SubscriptionStatusResponse("FREE", "none", null, false);
        }

        return new SubscriptionStatusResponse(
            sub.getPlan(),
            sub.getStatus(),
            sub.getCurrentPeriodEnd(),
            sub.getCancelAtPeriodEnd() != null && sub.getCancelAtPeriodEnd()
        );
    }

    /**
     * 取消订阅（在周期结束时取消）
     */
    @Transactional
    public SubscriptionStatusResponse cancelSubscription(Long userId) {
        StripeSubscription sub = billingPort.findActiveSubscriptionByUserId(userId);

        if (sub == null) {
            throw new RuntimeException("没有可取消的活跃订阅");
        }

        try {
            // Stripe HTTP 调用
            Subscription stripeSub = Subscription.retrieve(sub.getStripeSubscriptionId());
            SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
            stripeSub.update(updateParams);
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription {}: {}", sub.getStripeSubscriptionId(), e.getMessage(), e);
            throw new RuntimeException("取消订阅失败", e);
        }

        return doCancelSubscription(sub);
    }

    private SubscriptionStatusResponse doCancelSubscription(StripeSubscription sub) {
        sub.setCancelAtPeriodEnd(true);
        sub.setCanceledAt(LocalDateTime.now());
        sub.setLastWebhookEventId("manual_cancel_" + System.currentTimeMillis());
        billingPort.updateSubscription(sub);

        return new SubscriptionStatusResponse(
            sub.getPlan(),
            sub.getStatus(),
            sub.getCurrentPeriodEnd(),
            true
        );
    }

    /**
     * 创建 Portal Session（账单管理跳转）
     */
    public PortalSessionResponse createPortalSession(Long userId) {
        StripeCustomer customer = billingPort.findCustomerByUserIdAndNotDeleted(userId);

        if (customer == null) {
            throw new RuntimeException("未找到 Stripe 客户");
        }

        String url = paymentPort.createBillingPortalSession(customer.getStripeCustomerId(), stripeProperties.getCancelUrl());
        return new PortalSessionResponse(url);
    }

    // ==================== Webhook 事件处理 ====================

    /**
     * 处理 checkout.session.completed 事件
     */
    @Transactional
    public void handleCheckoutSessionCompleted(Event event) {
        // 反序列化
        Session session = deserializeSession(event, "checkout.session.completed");
        if (session == null) return;

        String userIdStr = session.getMetadata().get("userId");
        String planStr = session.getMetadata().get("plan");
        String billingCycleStr = session.getMetadata().get("billingCycle");

        if (userIdStr == null || planStr == null) {
            log.warn("checkout.session.completed: missing metadata in session {}", session.getId());
            return;
        }

        Long userId = Long.parseLong(userIdStr);
        SubscriptionPlan plan = validatePlan(planStr);
        BillingCycle billingCycle = BillingCycle.fromValue(billingCycleStr != null ? billingCycleStr : "monthly");

        // 获取 Stripe Subscription 对象（HTTP 调用，在事务外）
        String subscriptionId = session.getSubscription();
        if (subscriptionId == null) {
            log.warn("checkout.session.completed: no subscription in session {}", session.getId());
            return;
        }

        Subscription stripeSub;
        StripeCustomer customer;
        try {
            stripeSub = Subscription.retrieve(subscriptionId);
            customer = getOrCreateCustomer(userId);
        } catch (StripeException e) {
            log.error("checkout.session.completed: Stripe API error for session {}: {}", session.getId(), e.getMessage(), e);
            return;
        }

        // DB 操作在事务内
        doHandleCheckoutSessionCompleted(event, userId, plan, billingCycle, subscriptionId, stripeSub, customer);
    }

    private void doHandleCheckoutSessionCompleted(Event event, Long userId, SubscriptionPlan plan,
                                                   BillingCycle billingCycle, String subscriptionId,
                                                   Subscription stripeSub, StripeCustomer customer) {
        // 幂等检查：是否已存在该 subscription
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        // 幂等检查：是否已处理过该 event
        if (subRecord != null && event.getId().equals(subRecord.getLastWebhookEventId())) {
            log.info("checkout.session.completed: already processed event {}, skipping", event.getId());
            return;
        }

        if (subRecord == null) {
            // 插入新记录
            subRecord = new StripeSubscription();
            subRecord.setUserId(userId);
            subRecord.setStripeCustomerId(customer.getStripeCustomerId());
            subRecord.setStripeSubscriptionId(subscriptionId);
            subRecord.setPlan(plan.getValue());
            subRecord.setBillingCycle(billingCycle.getValue());
            subRecord.setStripePriceId(stripeSub.getItems().getData().get(0).getPrice().getId());
            subRecord.setStatus(stripeSub.getStatus());
            subRecord.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());

            if (stripeSub.getCurrentPeriodStart() != null) {
                subRecord.setCurrentPeriodStart(
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.systemDefault()));
            }
            if (stripeSub.getCurrentPeriodEnd() != null) {
                subRecord.setCurrentPeriodEnd(
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.systemDefault()));
            }

            subRecord.setLastWebhookEventId(event.getId());
            boolean insertSucceeded = false;

            try {
                billingPort.saveSubscription(subRecord);
                insertSucceeded = true;
            } catch (DuplicateKeyException e) {
                subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);
                if (subRecord == null) {
                    log.error("DuplicateKeyException caught but re-query returned null for subscriptionId {}", subscriptionId);
                    return;
                }
                log.info("checkout.session.completed: duplicate insert caught, re-queried existing record for subscriptionId {}", subscriptionId);
            }

            if (!insertSucceeded) {
                int claimed = atomicClaimEventId(subRecord.getId(), event.getId());
                if (claimed == 0) {
                    log.info("checkout.session.completed: event {} already claimed by concurrent thread, skipping", event.getId());
                    return;
                }
            }

            log.info("Created subscription record for user {}, plan {}, subscriptionId {}", userId, plan, subscriptionId);
        } else {
            int claimed = atomicClaimEventId(subRecord.getId(), event.getId());
            if (claimed == 0) {
                log.info("checkout.session.completed: event {} already claimed by concurrent thread for existing record, skipping", event.getId());
                return;
            }
        }

        // 更新 userLevel
        updateUserLevel(userId, plan.getValue(), "checkout.session.completed", event.getId());
    }

    /**
     * 处理 customer.subscription.updated 事件
     */
    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        Subscription stripeSub;
        try {
            var deserializer = event.getDataObjectDeserializer();
            stripeSub = (Subscription) deserializer.getObject().orElse(null);
            if (stripeSub == null) {
                stripeSub = (Subscription) deserializer.deserializeUnsafe();
                if (stripeSub == null) {
                    log.warn("subscription.updated: failed to deserialize event object");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("subscription.updated: deserialization error", e);
            return;
        }

        String subscriptionId = stripeSub.getId();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            log.warn("subscription.updated: no local record for subscription {}", subscriptionId);
            return;
        }

        // 原子幂等检查 + 更新：lastWebhookEventId 为 NULL 或与当前 event 不同且时间戳更新时才更新
        long eventCreated = event.getCreated();
        int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), stripeSub.getStatus(), eventCreated);

        if (rows == 0) {
            log.info("subscription.updated: already processed event {}, skipping", event.getId());
            return;
        }

        // 更新 period 等时间字段
        boolean hasTimeUpdate = false;
        LocalDateTime newPeriodStart = null;
        LocalDateTime newPeriodEnd = null;
        LocalDateTime newCanceledAt = null;
        if (stripeSub.getCurrentPeriodStart() != null) {
            newPeriodStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.systemDefault());
            hasTimeUpdate = true;
        }
        if (stripeSub.getCurrentPeriodEnd() != null) {
            newPeriodEnd = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.systemDefault());
            hasTimeUpdate = true;
        }
        if (stripeSub.getCanceledAt() != null) {
            newCanceledAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCanceledAt()), ZoneId.systemDefault());
            hasTimeUpdate = true;
        }

        if (hasTimeUpdate) {
            billingPort.updateSubscriptionFields(subRecord.getId(), newPeriodStart, newPeriodEnd, newCanceledAt);
        }

        // 同步 userLevel
        String newStatus = stripeSub.getStatus();
        if ("active".equals(newStatus) || "trialing".equals(newStatus)) {
            updateUserLevel(subRecord.getUserId(), subRecord.getPlan(), "subscription.updated -> " + newStatus, event.getId());
        } else if ("past_due".equals(newStatus)) {
            log.warn("subscription {}: past_due, not downgrading yet (grace period)", subscriptionId);
        } else if ("canceled".equals(newStatus) || "unpaid".equals(newStatus)) {
            updateUserLevel(subRecord.getUserId(), "FREE", "subscription.updated -> " + newStatus, event.getId());
        } else if ("paused".equals(newStatus)) {
            log.info("subscription {}: paused, keeping userLevel unchanged", subscriptionId);
        }

        log.info("Updated subscription {} status -> {}", subscriptionId, newStatus);
    }

    /**
     * 处理 customer.subscription.deleted 事件
     */
    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        Subscription stripeSub;
        try {
            var deserializer = event.getDataObjectDeserializer();
            stripeSub = (Subscription) deserializer.getObject().orElse(null);
            if (stripeSub == null) {
                stripeSub = (Subscription) deserializer.deserializeUnsafe();
                if (stripeSub == null) {
                    log.warn("subscription.deleted: failed to deserialize event object");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("subscription.deleted: deserialization error", e);
            return;
        }

        String subscriptionId = stripeSub.getId();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            log.warn("subscription.deleted: no local record for subscription {}", subscriptionId);
            return;
        }

        // 原子幂等检查 + 更新 + 事件时间戳排序校验
        long eventCreated = event.getCreated();
        int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "canceled", eventCreated);

        if (rows == 0) {
            log.info("subscription.deleted: already processed event {}, skipping", event.getId());
            return;
        }

        // 降级为 FREE
        updateUserLevel(subRecord.getUserId(), "FREE", "subscription.deleted", event.getId());
        log.info("Subscription {} deleted, user {} downgraded to FREE", subscriptionId, subRecord.getUserId());
    }

    /**
     * 处理 customer.subscription.resumed 事件
     */
    @Transactional
    public void handleSubscriptionResumed(Event event) {
        Subscription stripeSub;
        try {
            var deserializer = event.getDataObjectDeserializer();
            stripeSub = (Subscription) deserializer.getObject().orElse(null);
            if (stripeSub == null) {
                stripeSub = (Subscription) deserializer.deserializeUnsafe();
                if (stripeSub == null) {
                    log.warn("subscription.resumed: failed to deserialize event object");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("subscription.resumed: deserialization error", e);
            return;
        }

        String subscriptionId = stripeSub.getId();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            log.warn("subscription.resumed: no local record for subscription {}", subscriptionId);
            return;
        }

        long eventCreated = event.getCreated();
        int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), stripeSub.getStatus(), eventCreated);

        if (rows == 0) {
            log.info("subscription.resumed: already processed event {}, skipping", event.getId());
            return;
        }

        log.info("Subscription {} resumed, status: {}", subscriptionId, stripeSub.getStatus());
    }

    /**
     * 处理 invoice.payment_failed 事件
     */
    public void handleInvoicePaymentFailed(Event event) {
        com.stripe.model.Invoice invoice;
        try {
            var deserializer = event.getDataObjectDeserializer();
            invoice = (com.stripe.model.Invoice) deserializer.getObject().orElse(null);
            if (invoice == null) {
                invoice = (com.stripe.model.Invoice) deserializer.deserializeUnsafe();
                if (invoice == null) {
                    log.warn("invoice.payment_failed: failed to deserialize event object");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("invoice.payment_failed: deserialization error", e);
            return;
        }

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) {
            log.warn("invoice.payment_failed: no subscription in invoice");
            return;
        }

        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord != null) {
            long eventCreated = event.getCreated();
            int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "past_due", eventCreated);

            if (rows == 0) {
                log.info("invoice.payment_failed: already processed event {}, skipping", event.getId());
            } else {
                log.warn("invoice.payment_failed: subscription {} marked past_due (grace period applies)", subscriptionId);
            }
        } else {
            log.warn("invoice.payment_failed: no local record for subscription {}", subscriptionId);
        }
    }

    /**
     * 处理 invoice.payment_succeeded 事件（作为 checkout.session.completed 的fallback）
     */
    @Transactional
    public void handleInvoicePaymentSucceeded(Event event) {
        com.stripe.model.Invoice invoice = deserializeInvoice(event, "invoice.payment_succeeded");
        if (invoice == null) return;

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) {
            log.info("invoice.payment_succeeded: no subscription in invoice {}, skipping", event.getId());
            return;
        }

        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        // 已有记录且状态已是 active/trialing → 已由 checkout.session.completed 处理，跳过
        if (subRecord != null
            && ("active".equals(subRecord.getStatus()) || "trialing".equals(subRecord.getStatus()))) {
            log.info("invoice.payment_succeeded: subscription {} already active, skipping (handled by checkout.session.completed)",
                subscriptionId);
            return;
        }

        // 已有记录但状态不是 active → 用原子更新激活
        if (subRecord != null) {
            doActivateSubscriptionFromInvoice(event, subRecord);
            return;
        }

        // 无本地记录 → 孤立发票，需要创建完整订阅记录
        createSubscriptionFromOrphanedInvoice(event, invoice, subscriptionId);
    }

    /**
     * 反序列化 Invoice 对象
     */
    private com.stripe.model.Invoice deserializeInvoice(Event event, String eventType) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = (com.stripe.model.Invoice) deserializer.getObject().orElse(null);
            if (obj == null) {
                obj = (com.stripe.model.Invoice) deserializer.deserializeUnsafe();
                if (obj == null) {
                    log.warn("{}: failed to deserialize event object", eventType);
                    return null;
                }
            }
            return obj;
        } catch (Exception e) {
            log.error("{}: deserialization error", eventType, e);
            return null;
        }
    }

    /**
     * 激活已有订阅
     */
    private void doActivateSubscriptionFromInvoice(Event event, StripeSubscription subRecord) {
        long eventCreated = event.getCreated();
        int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "active", eventCreated);

        if (rows == 0) {
            log.info("invoice.payment_succeeded: already processed event {}, skipping", event.getId());
            return;
        }

        updateUserLevel(subRecord.getUserId(), subRecord.getPlan(), "invoice.payment_succeeded -> fallback activate", event.getId());
        log.info("invoice.payment_succeeded: fallback activated subscription {} for user {}",
            subRecord.getStripeSubscriptionId(), subRecord.getUserId());
    }

    /**
     * 从孤立发票创建订阅记录
     */
    private void createSubscriptionFromOrphanedInvoice(Event event,
                                                       com.stripe.model.Invoice invoice,
                                                       String subscriptionId) {
        String userIdStr = invoice.getMetadata() != null ? invoice.getMetadata().get("userId") : null;
        if (userIdStr == null) {
            log.warn("invoice.payment_succeeded: orphaned invoice {} has no userId in metadata, cannot create subscription",
                event.getId());
            return;
        }

        Long userId = Long.parseLong(userIdStr);

        // Stripe HTTP 调用：获取 customer 和 subscription
        StripeCustomer customer;
        Subscription stripeSub;
        try {
            customer = getOrCreateCustomer(userId);
            stripeSub = Subscription.retrieve(subscriptionId);
        } catch (StripeException e) {
            log.error("invoice.payment_succeeded: Stripe API error for orphaned invoice {}: {}",
                event.getId(), e.getMessage(), e);
            return;
        }

        // DB 操作在事务内
        doCreateSubscriptionFromOrphanedInvoice(event, userId, subscriptionId, stripeSub, customer, invoice);
    }

    private void doCreateSubscriptionFromOrphanedInvoice(Event event, Long userId,
                                                          String subscriptionId,
                                                          Subscription stripeSub,
                                                          StripeCustomer customer,
                                                          com.stripe.model.Invoice invoice) {
        // 双重检查：并发情况下可能已经被 checkout.session.completed 创建
        StripeSubscription existing = billingPort.findSubscriptionByStripeId(subscriptionId);
        if (existing != null
            && ("active".equals(existing.getStatus()) || "trialing".equals(existing.getStatus()))) {
            log.info("invoice.payment_succeeded: orphaned path race, subscription {} already active, skipping",
                subscriptionId);
            return;
        }

        StripeSubscription subRecord = new StripeSubscription();
        subRecord.setUserId(userId);
        subRecord.setStripeCustomerId(customer.getStripeCustomerId());
        subRecord.setStripeSubscriptionId(subscriptionId);
        subRecord.setLastWebhookEventId(event.getId());
        subRecord.setLastEventCreated(event.getCreated());

        if (stripeSub.getItems() != null && stripeSub.getItems().getData() != null
            && !stripeSub.getItems().getData().isEmpty()) {
            subRecord.setStripePriceId(stripeSub.getItems().getData().get(0).getPrice().getId());
        }

        subRecord.setStatus(stripeSub.getStatus());
        subRecord.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());

        if (stripeSub.getCurrentPeriodStart() != null) {
            subRecord.setCurrentPeriodStart(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.systemDefault()));
        }
        if (stripeSub.getCurrentPeriodEnd() != null) {
            subRecord.setCurrentPeriodEnd(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.systemDefault()));
        }

        // 从 Stripe Subscription metadata 或 invoice metadata 推断 plan
        String planStr = null;
        if (stripeSub.getMetadata() != null) {
            planStr = stripeSub.getMetadata().get("plan");
        }
        if (planStr == null && invoice.getMetadata() != null) {
            planStr = invoice.getMetadata().get("plan");
        }
        if (planStr == null) {
            planStr = "PRO"; // fallback default
            log.warn("invoice.payment_succeeded: orphaned invoice {} has no plan metadata, defaulting to PRO",
                event.getId());
        }
        subRecord.setPlan(planStr);

        String billingCycleStr = null;
        if (stripeSub.getMetadata() != null) {
            billingCycleStr = stripeSub.getMetadata().get("billingCycle");
        }
        if (billingCycleStr == null && invoice.getMetadata() != null) {
            billingCycleStr = invoice.getMetadata().get("billingCycle");
        }
        if (billingCycleStr == null) {
            billingCycleStr = "monthly";
        }
        subRecord.setBillingCycle(billingCycleStr);

        boolean insertSucceeded = false;
        try {
            billingPort.saveSubscription(subRecord);
            insertSucceeded = true;
        } catch (DuplicateKeyException e) {
            subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);
            if (subRecord == null) {
                log.error("DuplicateKeyException caught but re-query returned null for subscriptionId {}", subscriptionId);
                return;
            }
        }

        if (!insertSucceeded) {
            int claimed = atomicClaimEventId(subRecord.getId(), event.getId());
            if (claimed == 0) {
                log.info("invoice.payment_succeeded: event {} already claimed by concurrent thread, skipping", event.getId());
                return;
            }
        }

        updateUserLevel(userId, subRecord.getPlan(), "invoice.payment_succeeded -> orphaned create", event.getId());
        log.info("invoice.payment_succeeded: created subscription record from orphaned invoice for user {}, plan {}, subscriptionId {}",
            userId, subRecord.getPlan(), subscriptionId);
    }

    // ==================== 内部方法 ====================

    /**
     * 反序列化 Session 对象，供 checkout.session.completed 使用。
     */
    private Session deserializeSession(Event event, String eventType) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            Session session = (Session) deserializer.getObject().orElse(null);
            if (session == null) {
                session = (Session) deserializer.deserializeUnsafe();
                if (session == null) {
                    log.warn("{}: failed to deserialize event object", eventType);
                    return null;
                }
            }
            return session;
        } catch (Exception e) {
            log.error("{}: deserialization error", eventType, e);
            return null;
        }
    }

    private StripeCustomer getOrCreateCustomer(Long userId) {
        StripeCustomer existing = billingPort.findCustomerByUserIdAndNotDeleted(userId);

        if (existing != null) {
            return existing;
        }

        User user = userRepositoryPort.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .build();

            Customer customer = Customer.create(params);

            StripeCustomer newCustomer = new StripeCustomer();
            newCustomer.setUserId(userId);
            newCustomer.setStripeCustomerId(customer.getId());
            billingPort.saveCustomer(newCustomer);

            log.info("Created Stripe Customer for user {}: {}", userId, customer.getId());
            return newCustomer;
        } catch (StripeException e) {
            log.error("Failed to create Stripe Customer for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("创建 Stripe 客户失败", e);
        }
    }

    private String getPriceId(SubscriptionPlan plan, BillingCycle billingCycle) {
        Map<String, StripeProperties.PlanPrices> prices = stripeProperties.getPrices();
        if (prices == null) {
            throw new RuntimeException("Stripe prices not configured");
        }

        StripeProperties.PlanPrices planPrices = prices.get(plan.getValue().toLowerCase());
        if (planPrices == null) {
            throw new RuntimeException("No Stripe prices configured for plan: " + plan);
        }

        String priceId = switch (billingCycle) {
            case MONTHLY -> planPrices.getMonthlyPriceId();
            case YEARLY -> planPrices.getYearlyPriceId();
        };

        if (priceId == null || priceId.isBlank()) {
            throw new RuntimeException("No price configured for " + plan + " " + billingCycle);
        }

        return priceId;
    }

    private SubscriptionPlan validatePlan(String plan) {
        try {
            return SubscriptionPlan.fromValue(plan);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的套餐类型: " + plan + "，可选值: PRO, MAX");
        }
    }

    private BillingCycle validateBillingCycle(String billingCycle) {
        try {
            return BillingCycle.fromValue(billingCycle);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的计费周期: " + billingCycle + "，可选值: monthly, yearly");
        }
    }

    /**
     * 原子性地设置 lastWebhookEventId：只有当前值为 NULL 时才设置（用于 claim 事件处理权）
     */
    private int atomicClaimEventId(Long subscriptionRecordId, String eventId) {
        return billingPort.atomicUpdateSubscription(subscriptionRecordId, eventId, null, null);
    }

    /**
     * 基于 Redis SETNX 的事件级幂等检查，防止不同 event_id 的交叉并发重复处理
     * @return true = 首次处理，false = 已处理过
     */
    private boolean markEventProcessed(String eventId) {
        String key = "webhook:event_processed:" + eventId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofHours(24));
        return Boolean.TRUE.equals(success);
    }

    private void updateUserLevel(Long userId, String newLevel, String reason, String eventId) {
        // 事件级幂等检查：同一个 event_id 只能触发一次 updateUserLevel
        if (eventId != null && !markEventProcessed(eventId)) {
            log.info("updateUserLevel: event {} already processed, skipping", eventId);
            return;
        }

        updateUserLevel(userId, newLevel, reason);
    }

    private void updateUserLevel(Long userId, String newLevel, String reason) {
        User user = userRepositoryPort.findById(userId).orElse(null);
        if (user == null) {
            log.error("Cannot update userLevel: user {} not found", userId);
            return;
        }

        String oldLevel = user.getUserLevel();
        if (newLevel.equals(oldLevel)) {
            return; // 无需更新
        }

        user.setUserLevel(newLevel);
        userRepositoryPort.update(user);

        // 记录变更历史
        UserPlanHistory history = new UserPlanHistory();
        history.setUserId(userId);
        history.setOldPlan(oldLevel != null ? oldLevel : "UNKNOWN");
        history.setNewPlan(newLevel);
        history.setNote(reason);
        userRepositoryPort.savePlanHistory(history);

        log.info("User {} level changed: {} -> {} (reason: {})", userId, oldLevel, newLevel, reason);

        // 降级到 FREE 时吊销用户所有 JWT，防止退款后继续白嫖高级 API
        if ("FREE".equals(newLevel) && !"FREE".equals(oldLevel)) {
            revokeAllUserTokens(user.getEmail(), "subscription_downgrade: " + reason);
        }
    }

    /**
     * 吊销用户所有 JWT 令牌，使其立即失效。
     * 通过 email 级别的黑名单条目实现，任何该邮箱的 JWT 都会被拒绝。
     */
    private void revokeAllUserTokens(String email, String reason) {
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(7); // 黑名单保留 7 天
            tokenBlacklistService.blacklistAllUserTokens(email, reason, expiresAt);
            log.info("已吊销用户 {} 的所有 JWT 令牌（原因：{}）", email, reason);
        } catch (Exception e) {
            log.warn("吊销用户 JWT 失败: {}", e.getMessage());
        }
    }

}
