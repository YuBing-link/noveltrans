package com.yumu.noveltranslator.port.out;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.QuotaUsage;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeCustomer;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeSubscription;

import java.time.LocalDate;
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
    int updateSubscriptionByWrapper(LambdaUpdateWrapper<StripeSubscription> wrapper);

    // === QuotaUsage ===
    QuotaUsage findQuotaUsageByUserIdAndDate(Long userId, LocalDate date);
    void incrementQuotaUsage(Long userId, LocalDate date, long chars);
    void decrementQuotaUsage(Long userId, LocalDate date, long chars);
    long getMonthlyQuotaUsage(Long userId, LocalDate monthStart);
    void saveQuotaUsage(QuotaUsage quotaUsage);
}
