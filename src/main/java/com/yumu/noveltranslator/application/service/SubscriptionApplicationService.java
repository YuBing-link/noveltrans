package com.yumu.noveltranslator.application.service;

import com.stripe.model.Event;
import com.yumu.noveltranslator.domain.model.StripeCustomer;
import com.yumu.noveltranslator.domain.model.StripeSubscription;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPlanHistory;
import com.yumu.noveltranslator.enums.BillingCycle;
import com.yumu.noveltranslator.enums.SubscriptionPlan;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionRequest;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionResponse;
import com.yumu.noveltranslator.port.dto.subscription.PaymentVerificationResponse;
import com.yumu.noveltranslator.port.dto.subscription.PortalSessionResponse;
import com.yumu.noveltranslator.port.dto.subscription.SubscriptionStatusResponse;
import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.PaymentPort;
import com.yumu.noveltranslator.port.out.TokenRevocationPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.port.out.payment.CustomerInfo;
import com.yumu.noveltranslator.port.out.payment.PaymentSessionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionUpdateRequest;
import com.yumu.noveltranslator.properties.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * 订阅应用服务
 *
 * <p>事务边界原则：Stripe HTTP 调用在事务外执行（通过 PaymentPort），DB 操作通过 TransactionTemplate 编程式事务执行。
 */
@Slf4j
@Service
public class SubscriptionApplicationService implements com.yumu.noveltranslator.port.in.SubscriptionPort {

    private final StripeProperties stripeProperties;
    private final BillingRepositoryPort billingPort;
    private final UserRepositoryPort userRepositoryPort;
    private final StringRedisTemplate stringRedisTemplate;
    private final TokenRevocationPort tokenRevocationPort;
    private final PaymentPort paymentPort;
    private final PlatformTransactionManager transactionManager;

    private final TransactionTemplate tx;

    public SubscriptionApplicationService(StripeProperties stripeProperties,
                               BillingRepositoryPort billingPort,
                               UserRepositoryPort userRepositoryPort,
                               StringRedisTemplate stringRedisTemplate,
                               TokenRevocationPort tokenRevocationPort,
                               PaymentPort paymentPort,
                               PlatformTransactionManager transactionManager) {
        this.stripeProperties = stripeProperties;
        this.billingPort = billingPort;
        this.userRepositoryPort = userRepositoryPort;
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenRevocationPort = tokenRevocationPort;
        this.paymentPort = paymentPort;
        this.transactionManager = transactionManager;

        TransactionDefinition def = new TransactionDefinition() {
            @Override public int getPropagationBehavior() { return TransactionDefinition.PROPAGATION_REQUIRED; }
            @Override public int getIsolationLevel() { return TransactionDefinition.ISOLATION_DEFAULT; }
            @Override public int getTimeout() { return 30; }
            @Override public boolean isReadOnly() { return false; }
            @Override public String getName() { return "subscription-tx"; }
        };
        this.tx = new TransactionTemplate(transactionManager, def);
    }

    // ==================== 用户端 API ====================

    /**
     * 验证 Checkout Session 支付结果（前端回调时主动查询）
     */
    public PaymentVerificationResponse verifyCheckoutSession(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new PaymentVerificationResponse(false, sessionId, null, null, "缺少 session_id 参数");
        }

        try {
            PaymentSessionInfo session = paymentPort.retrieveCheckoutSession(sessionId);

            String metadataUserId = Optional.ofNullable(session.metadata())
                .map(m -> m.get("userId"))
                .orElse(null);
            if (metadataUserId == null || !metadataUserId.equals(String.valueOf(userId))) {
                return new PaymentVerificationResponse(false, sessionId, null, null, "支付会话不属于当前用户");
            }

            String paymentStatus = session.paymentStatus();
            String plan = Optional.ofNullable(session.metadata())
                .map(m -> m.get("plan"))
                .orElse(null);

            if ("paid".equals(paymentStatus)) {
                StripeSubscription sub = Optional.ofNullable(session.subscriptionId())
                    .map(billingPort::findSubscriptionByStripeId)
                    .orElse(null);

                if (sub != null) {
                    return new PaymentVerificationResponse(true, sessionId, plan, sub.getStatus(), "支付成功，订阅已激活");
                }
                return new PaymentVerificationResponse(true, sessionId, plan, "pending", "支付已确认，订阅正在激活中");
            } else if ("unpaid".equals(paymentStatus)) {
                return new PaymentVerificationResponse(false, sessionId, plan, "unpaid", "支付尚未完成");
            } else if ("no_payment_required".equals(paymentStatus)) {
                return new PaymentVerificationResponse(false, sessionId, plan, "no_payment_required", "无需支付");
            }

            return new PaymentVerificationResponse(false, sessionId, plan, paymentStatus, "支付状态: " + paymentStatus);
        } catch (Exception e) {
            log.error("Failed to verify checkout session {}: {}", sessionId, e.getMessage(), e);
            return new PaymentVerificationResponse(false, sessionId, null, null, "无法验证支付状态");
        }
    }

    /**
     * 创建订阅支付会话
     */
    public CheckoutSessionResponse createCheckoutSession(Long userId, CheckoutSessionRequest request) {
        SubscriptionPlan plan = validatePlan(request.getPlan());
        BillingCycle billingCycle = validateBillingCycle(request.getBillingCycle());
        String priceId = getPriceId(plan, billingCycle);

        // 1. Stripe HTTP 调用在事务外
        StripeCustomer customer = getOrCreateCustomer(userId);
        StripeSubscription existingSub = billingPort.findActiveSubscriptionByUserId(userId);

        // 2. 已有活跃订阅 → 升级现有订阅
        if (existingSub != null) {
            return upgradeSubscription(userId, existingSub, priceId, plan, billingCycle);
        }

        // 3. 无活跃订阅 → 创建新 Checkout Session
        try {
            String url = paymentPort.createCheckoutSession(
                customer.getStripeCustomerId(), priceId,
                stripeProperties.getSuccessUrl(), stripeProperties.getCancelUrl());
            return new CheckoutSessionResponse(url);
        } catch (Exception e) {
            log.error("Failed to create Stripe Checkout Session for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("创建支付会话失败", e);
        }
    }

    /**
     * 升级现有订阅
     */
    private CheckoutSessionResponse upgradeSubscription(Long userId,
                                                         StripeSubscription existingSub,
                                                         String newPriceId,
                                                         SubscriptionPlan newPlan,
                                                         BillingCycle billingCycle) {
        // Stripe HTTP 调用在事务外
        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest(
            existingSub.getStripeSubscriptionId(), newPriceId, null);

        SubscriptionInfo updated;
        try {
            // Retrieve to get the item ID
            SubscriptionInfo subInfo = paymentPort.retrieveSubscription(existingSub.getStripeSubscriptionId());
            String oldItemId = subInfo.firstItemId();
            if (oldItemId == null) {
                throw new IllegalStateException("Subscription " + existingSub.getStripeSubscriptionId() + " has no items");
            }

            updated = paymentPort.updateSubscription(existingSub.getStripeSubscriptionId(),
                new SubscriptionUpdateRequest(oldItemId, newPriceId, null));
        } catch (Exception e) {
            log.error("Failed to upgrade subscription for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("升级订阅失败", e);
        }

        // DB 操作在编程式事务内
        tx.execute(status -> {
            doUpgradeSubscription(existingSub, updated, newPriceId, newPlan, billingCycle);
            return null;
        });

        return new CheckoutSessionResponse(null, true);
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
            Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())
        );
    }

    /**
     * 取消订阅（在周期结束时取消）
     */
    public SubscriptionStatusResponse cancelSubscription(Long userId) {
        StripeSubscription sub = billingPort.findActiveSubscriptionByUserId(userId);

        if (sub == null) {
            throw new RuntimeException("没有可取消的活跃订阅");
        }

        // Stripe HTTP 调用在事务外
        try {
            paymentPort.updateSubscription(sub.getStripeSubscriptionId(),
                new SubscriptionUpdateRequest(null, null, true));
        } catch (Exception e) {
            log.error("Failed to cancel Stripe subscription {}: {}", sub.getStripeSubscriptionId(), e.getMessage(), e);
            throw new RuntimeException("取消订阅失败", e);
        }

        // DB 操作在编程式事务内
        return tx.execute(status -> doCancelSubscription(sub));
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

    public void handleCheckoutSessionCompleted(Event event) {
        SessionInfo session = deserializeSessionInfo(event, "checkout.session.completed");
        if (session == null) {
            throw new IllegalStateException("Failed to deserialize checkout.session.completed event: " + event.getId());
        }

        Map<String, String> metadata = session.metadata();
        String userIdStr = metadata != null ? metadata.get("userId") : null;
        String planStr = metadata != null ? metadata.get("plan") : null;
        String billingCycleStr = metadata != null ? metadata.get("billingCycle") : null;

        if (userIdStr == null || planStr == null) {
            throw new IllegalStateException("Missing required metadata in session " + session.id() + ": userId=" + userIdStr + ", plan=" + planStr);
        }

        Long userId = Long.parseLong(userIdStr);
        SubscriptionPlan plan = validatePlan(planStr);
        BillingCycle billingCycle = BillingCycle.fromValue(billingCycleStr != null ? billingCycleStr : "monthly");

        String subscriptionId = session.subscriptionId();
        if (subscriptionId == null) {
            throw new IllegalStateException("No subscription in session " + session.id());
        }

        // Stripe HTTP 调用在事务外
        SubscriptionInfo stripeSub;
        StripeCustomer customer;
        try {
            stripeSub = paymentPort.retrieveSubscription(subscriptionId);
            customer = getOrCreateCustomer(userId);
        } catch (Exception e) {
            log.error("checkout.session.completed: Stripe API error for session {}: {}", session.id(), e.getMessage(), e);
            throw new RuntimeException("Stripe API call failed for session " + session.id(), e);
        }

        // DB 操作在编程式事务内
        tx.execute(status -> {
            doHandleCheckoutSessionCompleted(event, userId, plan, billingCycle, subscriptionId, stripeSub, customer);
            return null;
        });
    }

    public void handleSubscriptionUpdated(Event event) {
        SubscriptionInfo stripeSub = deserializeSubscriptionInfo(event, "subscription.updated");
        if (stripeSub == null) {
            throw new IllegalStateException("Failed to deserialize subscription.updated event: " + event.getId());
        }

        String subscriptionId = stripeSub.id();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            log.warn("subscription.updated: no local record for subscription {}", subscriptionId);
            return;
        }

        tx.execute(status -> {
            long eventCreated = event.getCreated();
            int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), stripeSub.status(), eventCreated);

            if (rows == 0) {
                log.info("subscription.updated: already processed event {}, skipping", event.getId());
                return null;
            }

            // 更新时间字段
            LocalDateTime newPeriodStart = toLocalDateTime(stripeSub.currentPeriodStart());
            LocalDateTime newPeriodEnd = toLocalDateTime(stripeSub.currentPeriodEnd());
            LocalDateTime newCanceledAt = toLocalDateTime(stripeSub.canceledAt());
            if (newPeriodStart != null || newPeriodEnd != null || newCanceledAt != null) {
                billingPort.updateSubscriptionFields(subRecord.getId(), newPeriodStart, newPeriodEnd, newCanceledAt);
            }

            // 同步 userLevel（通过 afterCommit 保证 Redis 在 DB 提交后写入）
            scheduleAfterCommit(() -> syncUserLevelAfterStatusChange(subRecord, stripeSub.status(), event));
            return null;
        });
    }

    public void handleSubscriptionDeleted(Event event) {
        SubscriptionInfo stripeSub = deserializeSubscriptionInfo(event, "subscription.deleted");
        if (stripeSub == null) {
            throw new IllegalStateException("Failed to deserialize subscription.deleted event: " + event.getId());
        }

        String subscriptionId = stripeSub.id();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            throw new IllegalStateException("subscription.deleted: no local record for subscription " + subscriptionId);
        }

        tx.execute(status -> {
            long eventCreated = event.getCreated();
            int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "canceled", eventCreated);

            if (rows == 0) {
                log.info("subscription.deleted: already processed event {}, skipping", event.getId());
                return null;
            }

            scheduleAfterCommit(() -> updateUserLevel(subRecord.getUserId(), "FREE", "subscription.deleted", event.getId()));
            return null;
        });
    }

    public void handleSubscriptionResumed(Event event) {
        SubscriptionInfo stripeSub = deserializeSubscriptionInfo(event, "subscription.resumed");
        if (stripeSub == null) {
            throw new IllegalStateException("Failed to deserialize subscription.resumed event: " + event.getId());
        }

        String subscriptionId = stripeSub.id();
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord == null) {
            throw new IllegalStateException("subscription.resumed: no local record for subscription " + subscriptionId);
        }

        tx.execute(status -> {
            long eventCreated = event.getCreated();
            int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), stripeSub.status(), eventCreated);

            if (rows == 0) {
                log.info("subscription.resumed: already processed event {}, skipping", event.getId());
            }
            return null;
        });
    }

    public void handleInvoicePaymentFailed(Event event) {
        com.stripe.model.Invoice invoice = deserializeStripeObject(event, com.stripe.model.Invoice.class, "invoice.payment_failed");
        if (invoice == null) {
            throw new IllegalStateException("Failed to deserialize invoice.payment_failed event: " + event.getId());
        }

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) {
            log.warn("invoice.payment_failed: no subscription in invoice {}", event.getId());
            return;
        }

        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord != null) {
            tx.execute(status -> {
                long eventCreated = event.getCreated();
                int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "past_due", eventCreated);

                if (rows == 0) {
                    log.info("invoice.payment_failed: already processed event {}, skipping", event.getId());
                } else {
                    log.warn("invoice.payment_failed: subscription {} marked past_due (grace period applies)", subscriptionId);
                }
                return null;
            });
        } else {
            log.warn("invoice.payment_failed: no local record for subscription {}", subscriptionId);
        }
    }

    public void handleInvoicePaymentSucceeded(Event event) {
        com.stripe.model.Invoice invoice = deserializeStripeObject(event, com.stripe.model.Invoice.class, "invoice.payment_succeeded");
        if (invoice == null) {
            throw new IllegalStateException("Failed to deserialize invoice.payment_succeeded event: " + event.getId());
        }

        String subscriptionId = invoice.getSubscription();
        if (subscriptionId == null) {
            log.info("invoice.payment_succeeded: no subscription in invoice {}, skipping", event.getId());
            return;
        }

        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        // 已有记录且状态已是 active/trialing → 已由 checkout.session.completed 处理
        if (subRecord != null
            && ("active".equals(subRecord.getStatus()) || "trialing".equals(subRecord.getStatus()))) {
            log.info("invoice.payment_succeeded: subscription {} already active, skipping (handled by checkout.session.completed)",
                subscriptionId);
            return;
        }

        // 已有记录但状态不是 active → 用原子更新激活
        if (subRecord != null) {
            tx.execute(status -> {
                doActivateSubscriptionFromInvoice(event, subRecord);
                return null;
            });
            return;
        }

        // 无本地记录 → 从孤立发票创建订阅（Stripe HTTP 在事务外，DB 在事务内）
        createSubscriptionFromOrphanedInvoice(event, invoice, subscriptionId);
    }

    /**
     * 从孤立发票创建订阅记录（Stripe HTTP 调用在事务外）
     */
    private void createSubscriptionFromOrphanedInvoice(Event event,
                                                        com.stripe.model.Invoice invoice,
                                                        String subscriptionId) {
        String userIdStr = invoice.getMetadata() != null ? invoice.getMetadata().get("userId") : null;
        if (userIdStr == null) {
            throw new IllegalStateException("invoice.payment_succeeded: orphaned invoice " + event.getId()
                + " has no userId in metadata, cannot create subscription");
        }

        Long userId = Long.parseLong(userIdStr);

        StripeCustomer customer;
        SubscriptionInfo stripeSub;
        try {
            customer = getOrCreateCustomer(userId);
            stripeSub = paymentPort.retrieveSubscription(subscriptionId);
        } catch (Exception e) {
            log.error("invoice.payment_succeeded: Stripe API error for orphaned invoice {}: {}",
                event.getId(), e.getMessage(), e);
            throw new RuntimeException("Stripe API call failed for orphaned invoice " + event.getId(), e);
        }

        tx.execute(status -> {
            doCreateSubscriptionFromOrphanedInvoice(event, userId, subscriptionId, stripeSub, customer, invoice);
            return null;
        });
    }

    // ==================== 窄事务 DB 操作方法（由 TransactionTemplate 包裹） ====================

    void doHandleCheckoutSessionCompleted(Event event, Long userId, SubscriptionPlan plan,
                                           BillingCycle billingCycle, String subscriptionId,
                                           SubscriptionInfo stripeSub, StripeCustomer customer) {
        StripeSubscription subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);

        if (subRecord != null && event.getId().equals(subRecord.getLastWebhookEventId())) {
            log.info("checkout.session.completed: already processed event {}, skipping", event.getId());
            return;
        }

        if (subRecord == null) {
            subRecord = new StripeSubscription();
            subRecord.setUserId(userId);
            subRecord.setStripeCustomerId(customer.getStripeCustomerId());
            subRecord.setStripeSubscriptionId(subscriptionId);
            subRecord.setPlan(plan.getValue());
            subRecord.setBillingCycle(billingCycle.getValue());

            subRecord.setStripePriceId(stripeSub.priceId());
            subRecord.setStatus(stripeSub.status());
            subRecord.setCancelAtPeriodEnd(stripeSub.cancelAtPeriodEnd());
            subRecord.setCurrentPeriodStart(toLocalDateTime(stripeSub.currentPeriodStart()));
            subRecord.setCurrentPeriodEnd(toLocalDateTime(stripeSub.currentPeriodEnd()));
            subRecord.setLastWebhookEventId(event.getId());
            subRecord.setLastEventCreated(event.getCreated());

            boolean insertSucceeded = false;
            try {
                billingPort.saveSubscription(subRecord);
                insertSucceeded = true;
            } catch (DuplicateKeyException e) {
                subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);
                if (subRecord == null) {
                    throw new IllegalStateException("DuplicateKeyException but re-query returned null for subscriptionId " + subscriptionId, e);
                }
                log.info("checkout.session.completed: duplicate insert caught, re-queried existing record for subscriptionId {}", subscriptionId);
            }

            if (!insertSucceeded) {
                int claimed = billingPort.claimWebhookEvent(subRecord.getId(), event.getId());
                if (claimed == 0) {
                    log.info("checkout.session.completed: event {} already claimed by concurrent thread, skipping", event.getId());
                    return;
                }
            }

            log.info("Created subscription record for user {}, plan {}, subscriptionId {}", userId, plan, subscriptionId);
        } else {
            int claimed = billingPort.claimWebhookEvent(subRecord.getId(), event.getId());
            if (claimed == 0) {
                log.info("checkout.session.completed: event {} already claimed by concurrent thread for existing record, skipping", event.getId());
                return;
            }
        }

        scheduleAfterCommit(() -> updateUserLevel(userId, plan.getValue(), "checkout.session.completed", event.getId()));
    }

    void doUpgradeSubscription(StripeSubscription existingSub,
                                SubscriptionInfo updated,
                                String newPriceId,
                                SubscriptionPlan newPlan,
                                BillingCycle billingCycle) {
        String oldPlan = existingSub.getPlan();
        existingSub.setPlan(newPlan.getValue());
        existingSub.setBillingCycle(billingCycle.getValue());
        existingSub.setStripePriceId(updated.priceId());
        existingSub.setStatus(updated.status());
        existingSub.setCancelAtPeriodEnd(updated.cancelAtPeriodEnd());
        existingSub.setCurrentPeriodStart(toLocalDateTime(updated.currentPeriodStart()));
        existingSub.setCurrentPeriodEnd(toLocalDateTime(updated.currentPeriodEnd()));
        existingSub.setLastOperationSource("upgrade_" + System.currentTimeMillis());
        billingPort.updateSubscription(existingSub);

        scheduleAfterCommit(() -> updateUserLevel(
            existingSub.getUserId(), newPlan.getValue(), "subscription_upgrade"));

        log.info("Upgraded subscription {} for user {}: {} -> {}, priceId {}",
            existingSub.getStripeSubscriptionId(), existingSub.getUserId(),
            oldPlan, newPlan, newPriceId);
    }

    SubscriptionStatusResponse doCancelSubscription(StripeSubscription sub) {
        sub.setCancelAtPeriodEnd(true);
        sub.setCanceledAt(LocalDateTime.now(ZoneId.of("UTC")));
        sub.setLastOperationSource("manual_cancel_" + System.currentTimeMillis());
        billingPort.updateSubscription(sub);

        return new SubscriptionStatusResponse(
            sub.getPlan(),
            sub.getStatus(),
            sub.getCurrentPeriodEnd(),
            true
        );
    }

    void doActivateSubscriptionFromInvoice(Event event, StripeSubscription subRecord) {
        long eventCreated = event.getCreated();
        int rows = billingPort.atomicUpdateSubscription(subRecord.getId(), event.getId(), "active", eventCreated);

        if (rows == 0) {
            log.info("invoice.payment_succeeded: already processed event {}, skipping", event.getId());
            return;
        }

        scheduleAfterCommit(() -> updateUserLevel(
            subRecord.getUserId(), subRecord.getPlan(),
            "invoice.payment_succeeded -> fallback activate", event.getId()));

        log.info("invoice.payment_succeeded: fallback activated subscription {} for user {}",
            subRecord.getStripeSubscriptionId(), subRecord.getUserId());
    }

    void doCreateSubscriptionFromOrphanedInvoice(Event event, Long userId,
                                                  String subscriptionId,
                                                  SubscriptionInfo stripeSub,
                                                  StripeCustomer customer,
                                                  com.stripe.model.Invoice invoice) {
        // 双重检查：并发情况下可能已被 checkout.session.completed 创建
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

        subRecord.setStripePriceId(stripeSub.priceId());
        subRecord.setStatus(stripeSub.status());
        subRecord.setCancelAtPeriodEnd(stripeSub.cancelAtPeriodEnd());
        subRecord.setCurrentPeriodStart(toLocalDateTime(stripeSub.currentPeriodStart()));
        subRecord.setCurrentPeriodEnd(toLocalDateTime(stripeSub.currentPeriodEnd()));

        // 从 Stripe Subscription metadata 或 invoice metadata 推断 plan
        String planStr = null;
        // Try to get metadata from the original event object
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = deserializer.getObject().orElse(null);
            if (obj instanceof com.stripe.model.Subscription s && s.getMetadata() != null) {
                planStr = s.getMetadata().get("plan");
            }
        } catch (Exception ignored) {}

        if (planStr == null && invoice.getMetadata() != null) {
            planStr = invoice.getMetadata().get("plan");
        }
        if (planStr == null) {
            planStr = "PRO";
            log.warn("invoice.payment_succeeded: orphaned invoice {} has no plan metadata, defaulting to PRO", event.getId());
        }
        subRecord.setPlan(planStr);

        String billingCycleStr = "monthly";
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = deserializer.getObject().orElse(null);
            if (obj instanceof com.stripe.model.Subscription s && s.getMetadata() != null) {
                billingCycleStr = s.getMetadata().getOrDefault("billingCycle", "monthly");
            }
        } catch (Exception ignored) {}
        if (invoice.getMetadata() != null && invoice.getMetadata().containsKey("billingCycle")) {
            billingCycleStr = invoice.getMetadata().get("billingCycle");
        }
        subRecord.setBillingCycle(billingCycleStr);

        boolean insertSucceeded = false;
        try {
            billingPort.saveSubscription(subRecord);
            insertSucceeded = true;
        } catch (DuplicateKeyException e) {
            subRecord = billingPort.findSubscriptionByStripeId(subscriptionId);
            if (subRecord == null) {
                throw new IllegalStateException("DuplicateKeyException but re-query returned null for subscriptionId " + subscriptionId, e);
            }
        }

        // Capture final reference for lambda
        final StripeSubscription savedRecord = subRecord;

        if (!insertSucceeded) {
            int claimed = billingPort.claimWebhookEvent(savedRecord.getId(), event.getId());
            if (claimed == 0) {
                log.info("invoice.payment_succeeded: event {} already claimed by concurrent thread, skipping", event.getId());
                return;
            }
        }

        scheduleAfterCommit(() -> updateUserLevel(
            userId, savedRecord.getPlan(), "invoice.payment_succeeded -> orphaned create", event.getId()));
    }

    // ==================== 内部工具方法 ====================

    /**
     * 泛型反序列化：提取 Stripe 对象，减少重复代码
     */
    private <T> T deserializeStripeObject(Event event, Class<T> clazz, String eventType) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            @SuppressWarnings("unchecked")
            T obj = (T) deserializer.getObject().orElse(null);
            if (obj == null) {
                obj = clazz.cast(deserializer.deserializeUnsafe());
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
     * 内部 record：从事件反序列化的 session 信息
     */
    record SessionInfo(String id, String subscriptionId, Map<String, String> metadata) {}

    private SessionInfo deserializeSessionInfo(Event event, String eventType) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = deserializer.getObject().orElse(null);
            if (obj == null) {
                obj = deserializer.deserializeUnsafe();
            }
            if (obj instanceof com.stripe.model.checkout.Session session) {
                return new SessionInfo(session.getId(), session.getSubscription(), session.getMetadata());
            }
            log.warn("{}: failed to deserialize event object", eventType);
            return null;
        } catch (Exception e) {
            log.error("{}: deserialization error", eventType, e);
            return null;
        }
    }

    private SubscriptionInfo deserializeSubscriptionInfo(Event event, String eventType) {
        try {
            var deserializer = event.getDataObjectDeserializer();
            var obj = deserializer.getObject().orElse(null);
            if (obj == null) {
                obj = deserializer.deserializeUnsafe();
            }
            if (obj instanceof com.stripe.model.Subscription sub) {
                String firstItemId = Optional.ofNullable(sub.getItems())
                    .map(items -> items.getData())
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0).getId())
                    .orElse(null);
                String priceId = Optional.ofNullable(sub.getItems())
                    .map(items -> items.getData())
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0))
                    .map(item -> item.getPrice())
                    .filter(price -> price != null)
                    .map(price -> price.getId())
                    .orElse(null);
                return new SubscriptionInfo(
                    sub.getId(), sub.getStatus(),
                    sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(),
                    sub.getCancelAtPeriodEnd(), firstItemId, priceId, sub.getCanceledAt());
            }
            log.warn("{}: failed to deserialize event object", eventType);
            return null;
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
            throw new RuntimeException("用户不存在，userId=" + userId);
        }

        try {
            CustomerInfo customer = paymentPort.createCustomer(user.getEmail());
            StripeCustomer newCustomer = new StripeCustomer();
            newCustomer.setUserId(userId);
            newCustomer.setStripeCustomerId(customer.id());
            billingPort.saveCustomer(newCustomer);

            log.info("Created Stripe Customer for user {}: {}", userId, customer.id());
            return newCustomer;
        } catch (Exception e) {
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
            String validValues = String.join(", ", SubscriptionPlan.VALUES);
            throw new IllegalArgumentException("无效的套餐类型: " + plan + "，可选值: " + validValues);
        }
    }

    private BillingCycle validateBillingCycle(String billingCycle) {
        try {
            return BillingCycle.fromValue(billingCycle);
        } catch (IllegalArgumentException e) {
            String validValues = String.join(", ", BillingCycle.VALUES);
            throw new IllegalArgumentException("无效的计费周期: " + billingCycle + "，可选值: " + validValues);
        }
    }

    /**
     * 将 Stripe epoch 秒数转换为 UTC LocalDateTime
     */
    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"));
    }

    /**
     * 注册在事务提交后执行的回调，用于 Redis 幂等标记等非事务操作。
     */
    private void scheduleAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 基于 Redis SETNX 的事件级幂等检查
     */
    private boolean markEventProcessed(String eventId) {
        String key = "webhook:event_processed:" + eventId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofHours(24));
        return Boolean.TRUE.equals(success);
    }

    /**
     * 同步用户等级（状态变更后调用）
     */
    private void syncUserLevelAfterStatusChange(StripeSubscription subRecord, String newStatus, Event event) {
        String targetLevel;
        if ("active".equals(newStatus) || "trialing".equals(newStatus)) {
            targetLevel = subRecord.getPlan();
        } else if ("past_due".equals(newStatus)) {
            log.warn("subscription {}: past_due, not downgrading yet (grace period)", subRecord.getStripeSubscriptionId());
            return;
        } else if ("canceled".equals(newStatus) || "unpaid".equals(newStatus)) {
            targetLevel = "FREE";
        } else if ("paused".equals(newStatus)) {
            log.info("subscription {}: paused, keeping userLevel unchanged", subRecord.getStripeSubscriptionId());
            return;
        } else {
            log.info("subscription {}: unknown status {}, keeping userLevel unchanged", subRecord.getStripeSubscriptionId(), newStatus);
            return;
        }

        scheduleAfterCommit(() -> updateUserLevel(subRecord.getUserId(), targetLevel,
            "subscription.updated -> " + newStatus, event.getId()));
    }

    private void updateUserLevel(Long userId, String newLevel, String reason, String eventId) {
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
            return;
        }

        user.setUserLevel(newLevel);
        userRepositoryPort.update(user);

        UserPlanHistory history = new UserPlanHistory();
        history.setUserId(userId);
        history.setOldPlan(oldLevel != null ? oldLevel : "UNKNOWN");
        history.setNewPlan(newLevel);
        history.setNote(reason);
        userRepositoryPort.savePlanHistory(history);

        log.info("User {} level changed: {} -> {} (reason: {})", userId, oldLevel, newLevel, reason);

        // 降级到 FREE 时：通过端口吊销令牌
        if ("FREE".equals(newLevel) && !"FREE".equals(oldLevel)) {
            tokenRevocationPort.revokeAllTokens(userId, user.getEmail(), "subscription_downgrade: " + reason);
        }
    }
}
