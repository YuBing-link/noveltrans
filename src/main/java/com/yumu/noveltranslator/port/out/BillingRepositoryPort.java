package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.domain.model.QuotaUsage;
import com.yumu.noveltranslator.domain.model.StripeCustomer;
import com.yumu.noveltranslator.domain.model.StripeSubscription;
import com.yumu.noveltranslator.domain.model.Tenant;
import com.yumu.noveltranslator.domain.model.TokenBlacklist;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillingRepositoryPort {

    // === StripeCustomer ===
    Optional<StripeCustomer> findCustomerByUserId(Long userId);
    StripeCustomer findCustomerByUserIdAndNotDeleted(Long userId);
    void saveCustomer(StripeCustomer customer);
    void updateCustomer(StripeCustomer customer);

    // === StripeSubscription ===
    Optional<StripeSubscription> findSubscriptionByUserId(Long userId);
    StripeSubscription findActiveSubscriptionByUserId(Long userId);
    StripeSubscription findSubscriptionByStripeId(String stripeSubscriptionId);
    List<StripeSubscription> findSubscriptionsByUserId(Long userId);
    void saveSubscription(StripeSubscription subscription);
    void updateSubscription(StripeSubscription subscription);

    /**
     * 原子幂等更新：仅当 lastWebhookEventId 为 NULL 或与传入的 eventId 不同时才更新。
     * 返回受影响的行数（0 表示该 event 已处理过，应跳过）。
     */
    int atomicUpdateSubscription(Long id, String eventId, String status, Long eventCreated);

    /**
     * 更新订阅时间字段（period start/end, canceledAt）。在 atomicUpdateSubscription 成功后调用。
     */
    void updateSubscriptionFields(Long id, LocalDateTime periodStart, LocalDateTime periodEnd, LocalDateTime canceledAt);

    // === QuotaUsage ===
    QuotaUsage findQuotaUsageByUserIdAndDate(Long userId, LocalDate date);
    void incrementQuotaUsage(Long userId, LocalDate date, long chars);
    void decrementQuotaUsage(Long userId, LocalDate date, long chars);
    long getMonthlyQuotaUsage(Long userId, LocalDate monthStart);
    void saveQuotaUsage(QuotaUsage quotaUsage);
}
