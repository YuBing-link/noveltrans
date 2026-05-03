package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.CustomerCreateParams;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.StripeCustomer;
import com.yumu.noveltranslator.entity.StripeSubscription;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPlanHistory;
import com.yumu.noveltranslator.enums.BillingCycle;
import com.yumu.noveltranslator.enums.SubscriptionPlan;
import com.yumu.noveltranslator.mapper.StripeCustomerMapper;
import com.yumu.noveltranslator.mapper.StripeSubscriptionMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.UserPlanHistoryMapper;
import com.yumu.noveltranslator.properties.StripeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
public class SubscriptionService {

    private final StripeProperties stripeProperties;
    private final StripeCustomerMapper stripeCustomerMapper;
    private final StripeSubscriptionMapper stripeSubscriptionMapper;
    private final UserMapper userMapper;
    private final UserPlanHistoryMapper userPlanHistoryMapper;

    // ==================== 用户端 API ====================

    /**
     * 验证 Checkout Session 支付结果（前端回调时主动查询）
     */
    public PaymentVerificationResponse verifyCheckoutSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new PaymentVerificationResponse(false, null, null, null, "缺少 session_id 参数");
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
                StripeSubscription sub = stripeSubscriptionMapper.selectOne(
                    new LambdaQueryWrapper<StripeSubscription>()
                        .eq(StripeSubscription::getStripeSubscriptionId, session.getSubscription())
                );

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

        // 1. 获取或创建 Stripe Customer
        StripeCustomer customer = getOrCreateCustomer(userId);

        // 2. 检查是否已有活跃订阅
        StripeSubscription existingSub = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getUserId, userId)
                .eq(StripeSubscription::getDeleted, 0)
                .in(StripeSubscription::getStatus, "active", "trialing")
                .last("LIMIT 1")
        );

        // 3. 已有活跃订阅 → 升级现有订阅（不创建新 session）
        if (existingSub != null) {
            return upgradeSubscription(existingSub, priceId, plan, billingCycle);
        }

        // 4. 无活跃订阅 → 创建新 Checkout Session
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
     * 升级现有订阅：替换 price，Stripe 自动按比例结算差价
     */
    private CheckoutSessionResponse upgradeSubscription(StripeSubscription existingSub,
                                                         String newPriceId,
                                                         SubscriptionPlan newPlan,
                                                         BillingCycle billingCycle) {
        try {
            Subscription stripeSub = Subscription.retrieve(existingSub.getStripeSubscriptionId());

            // 删除旧的价格项
            String oldItemId = stripeSub.getItems().getData().get(0).getId();
            SubscriptionUpdateParams.Item removeItem = SubscriptionUpdateParams.Item.builder()
                .setId(oldItemId)
                .setDeleted(true)
                .setClearUsage(true)
                .build();

            // 添加新的价格项
            SubscriptionUpdateParams.Item newItem = SubscriptionUpdateParams.Item.builder()
                .setPrice(newPriceId)
                .setQuantity(1L)
                .build();

            SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                .addItem(removeItem)
                .addItem(newItem)
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                .build();

            Subscription updated = stripeSub.update(updateParams);

            // 更新本地记录
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
            stripeSubscriptionMapper.updateById(existingSub);

            // 更新用户等级
            updateUserLevel(existingSub.getUserId(), newPlan.getValue(), "subscription_upgrade");

            log.info("Upgraded subscription {} for user {}: {} -> {}, priceId {}",
                existingSub.getStripeSubscriptionId(), existingSub.getUserId(),
                existingSub.getPlan(), newPlan, newPriceId);

            return new CheckoutSessionResponse(null);
        } catch (StripeException e) {
            log.error("Failed to upgrade subscription for user {}: {}", existingSub.getUserId(), e.getMessage(), e);
            throw new RuntimeException("升级订阅失败", e);
        }
    }

    /**
     * 获取当前订阅状态
     */
    public SubscriptionStatusResponse getSubscriptionStatus(Long userId) {
        StripeSubscription sub = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getUserId, userId)
                .eq(StripeSubscription::getDeleted, 0)
                .orderByDesc(StripeSubscription::getCreateTime)
                .last("LIMIT 1")
        );

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
        StripeSubscription sub = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getUserId, userId)
                .eq(StripeSubscription::getDeleted, 0)
                .in(StripeSubscription::getStatus, "active", "trialing")
                .last("LIMIT 1")
        );

        if (sub == null) {
            throw new RuntimeException("没有可取消的活跃订阅");
        }

        try {
            Subscription stripeSub = Subscription.retrieve(sub.getStripeSubscriptionId());
            SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
            stripeSub.update(updateParams);

            sub.setCancelAtPeriodEnd(true);
            sub.setCanceledAt(LocalDateTime.now());
            sub.setLastWebhookEventId("manual_cancel_" + System.currentTimeMillis());
            stripeSubscriptionMapper.updateById(sub);

            return new SubscriptionStatusResponse(
                sub.getPlan(),
                sub.getStatus(),
                sub.getCurrentPeriodEnd(),
                true
            );
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription {}: {}", sub.getStripeSubscriptionId(), e.getMessage(), e);
            throw new RuntimeException("取消订阅失败", e);
        }
    }

    /**
     * 创建 Portal Session（账单管理跳转）
     */
    public PortalSessionResponse createPortalSession(Long userId) {
        StripeCustomer customer = stripeCustomerMapper.selectOne(
            new LambdaQueryWrapper<StripeCustomer>()
                .eq(StripeCustomer::getUserId, userId)
                .eq(StripeCustomer::getDeleted, 0)
        );

        if (customer == null) {
            throw new RuntimeException("未找到 Stripe 客户");
        }

        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customer.getStripeCustomerId())
                    .setReturnUrl(stripeProperties.getCancelUrl())
                    .build();

            com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(params);

            return new PortalSessionResponse(portalSession.getUrl());
        } catch (StripeException e) {
            log.error("Failed to create Stripe Portal Session for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("创建账单管理链接失败", e);
        }
    }

    // ==================== Webhook 事件处理 ====================

    /**
     * 处理 checkout.session.completed 事件
     */
    @Transactional
    public void handleCheckoutSessionCompleted(Event event) {
        Session session;
        try {
            var deserializer = event.getDataObjectDeserializer();
            session = (Session) deserializer.getObject().orElse(null);
            if (session == null) {
                // API 版本不匹配时尝试 unsafe 反序列化
                session = (Session) deserializer.deserializeUnsafe();
                if (session == null) {
                    log.warn("checkout.session.completed: failed to deserialize event object");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("checkout.session.completed: deserialization error", e);
            return;
        }

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

        // 获取 Stripe Subscription 对象
        StripeSubscription subRecord;
        try {
            // 展开 subscription 对象以获取 ID
            String subscriptionId = session.getSubscription();
            if (subscriptionId == null) {
                log.warn("checkout.session.completed: no subscription in session {}", session.getId());
                return;
            }

            Subscription stripeSub = Subscription.retrieve(subscriptionId);

            // 获取或创建 Stripe Customer
            StripeCustomer customer = getOrCreateCustomer(userId);

            // 幂等检查：是否已存在该 subscription
            subRecord = stripeSubscriptionMapper.selectOne(
                new LambdaQueryWrapper<StripeSubscription>()
                    .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
            );

            if (subRecord == null) {
                // 插入新记录 — 并发 webhook 可能触发 DuplicateKeyException（唯一索引兜底）
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

                try {
                    stripeSubscriptionMapper.insert(subRecord);
                } catch (DuplicateKeyException e) {
                    // 另一条线程已插入成功，重新查询获取记录
                    subRecord = stripeSubscriptionMapper.selectOne(
                        new LambdaQueryWrapper<StripeSubscription>()
                            .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
                    );
                    if (subRecord == null) {
                        log.error("DuplicateKeyException caught but re-query returned null for subscriptionId {}", subscriptionId);
                        return; // 异常情况，不处理
                    }
                    log.info("checkout.session.completed: duplicate insert caught, re-queried existing record for subscriptionId {}", subscriptionId);
                }

                log.info("Created subscription record for user {}, plan {}, subscriptionId {}", userId, plan, subscriptionId);
            }

            subRecord.setLastWebhookEventId(event.getId());
            stripeSubscriptionMapper.updateById(subRecord);

            // 更新 userLevel
            updateUserLevel(userId, plan.getValue(), "checkout.session.completed");

        } catch (StripeException e) {
            log.error("checkout.session.completed: Stripe API error for session {}: {}", session.getId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
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
        StripeSubscription subRecord = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
        );

        if (subRecord == null) {
            log.warn("subscription.updated: no local record for subscription {}", subscriptionId);
            return;
        }

        // 幂等检查
        if (event.getId().equals(subRecord.getLastWebhookEventId())) {
            log.info("subscription.updated: already processed event {}, skipping", event.getId());
            return;
        }

        String oldStatus = subRecord.getStatus();
        String newStatus = stripeSub.getStatus();

        // 更新状态
        subRecord.setStatus(newStatus);
        subRecord.setCancelAtPeriodEnd(stripeSub.getCancelAtPeriodEnd());
        subRecord.setStripePriceId(stripeSub.getItems().getData().get(0).getPrice().getId());

        if (stripeSub.getCurrentPeriodStart() != null) {
            subRecord.setCurrentPeriodStart(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.systemDefault()));
        }
        if (stripeSub.getCurrentPeriodEnd() != null) {
            subRecord.setCurrentPeriodEnd(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.systemDefault()));
        }

        if (stripeSub.getCanceledAt() != null) {
            subRecord.setCanceledAt(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCanceledAt()), ZoneId.systemDefault()));
        }

        subRecord.setLastWebhookEventId(event.getId());
        stripeSubscriptionMapper.updateById(subRecord);

        // 同步 userLevel
        if ("active".equals(newStatus) || "trialing".equals(newStatus)) {
            // 从 metadata 或现有记录获取 plan
            updateUserLevel(subRecord.getUserId(), subRecord.getPlan(), "subscription.updated -> " + newStatus);
        } else if ("past_due".equals(newStatus)) {
            log.warn("subscription {}: past_due, not downgrading yet (grace period)", subscriptionId);
        } else if ("canceled".equals(newStatus) || "unpaid".equals(newStatus)) {
            updateUserLevel(subRecord.getUserId(), "FREE", "subscription.updated -> " + newStatus);
        } else if ("paused".equals(newStatus)) {
            log.info("subscription {}: paused, keeping userLevel unchanged", subscriptionId);
        }

        log.info("Updated subscription {} status: {} -> {}", subscriptionId, oldStatus, newStatus);
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
        StripeSubscription subRecord = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
        );

        if (subRecord == null) {
            log.warn("subscription.deleted: no local record for subscription {}", subscriptionId);
            return;
        }

        // 幂等检查
        if (event.getId().equals(subRecord.getLastWebhookEventId())) {
            log.info("subscription.deleted: already processed event {}, skipping", event.getId());
            return;
        }

        subRecord.setStatus("canceled");
        subRecord.setLastWebhookEventId(event.getId());
        stripeSubscriptionMapper.updateById(subRecord);

        // 降级为 FREE
        updateUserLevel(subRecord.getUserId(), "FREE", "subscription.deleted");
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
        StripeSubscription subRecord = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
        );

        if (subRecord == null) {
            log.warn("subscription.resumed: no local record for subscription {}", subscriptionId);
            return;
        }

        subRecord.setStatus(stripeSub.getStatus());
        subRecord.setLastWebhookEventId(event.getId());
        stripeSubscriptionMapper.updateById(subRecord);

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

        StripeSubscription subRecord = stripeSubscriptionMapper.selectOne(
            new LambdaQueryWrapper<StripeSubscription>()
                .eq(StripeSubscription::getStripeSubscriptionId, subscriptionId)
        );

        if (subRecord != null) {
            subRecord.setStatus("past_due");
            subRecord.setLastWebhookEventId(event.getId());
            stripeSubscriptionMapper.updateById(subRecord);
            log.warn("invoice.payment_failed: subscription {} marked past_due (grace period applies)", subscriptionId);
        } else {
            log.warn("invoice.payment_failed: no local record for subscription {}", subscriptionId);
        }
    }

    // ==================== 内部方法 ====================

    private StripeCustomer getOrCreateCustomer(Long userId) {
        StripeCustomer existing = stripeCustomerMapper.selectOne(
            new LambdaQueryWrapper<StripeCustomer>()
                .eq(StripeCustomer::getUserId, userId)
                .eq(StripeCustomer::getDeleted, 0)
        );

        if (existing != null) {
            return existing;
        }

        User user = userMapper.selectById(userId);
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
            stripeCustomerMapper.insert(newCustomer);

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

    private void updateUserLevel(Long userId, String newLevel, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.error("Cannot update userLevel: user {} not found", userId);
            return;
        }

        String oldLevel = user.getUserLevel();
        if (newLevel.equals(oldLevel)) {
            return; // 无需更新
        }

        user.setUserLevel(newLevel);
        userMapper.updateById(user);

        // 记录变更历史
        UserPlanHistory history = new UserPlanHistory();
        history.setUserId(userId);
        history.setOldPlan(oldLevel != null ? oldLevel : "UNKNOWN");
        history.setNewPlan(newLevel);
        history.setNote(reason);
        userPlanHistoryMapper.insert(history);

        log.info("User {} level changed: {} -> {} (reason: {})", userId, oldLevel, newLevel, reason);
    }
}
