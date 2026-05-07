package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.QuotaUsage;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeCustomer;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeSubscription;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.QuotaUsageMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.StripeCustomerMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.StripeSubscriptionMapper;
import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BillingRepositoryAdapter implements BillingRepositoryPort {

    private final StripeCustomerMapper stripeCustomerMapper;
    private final StripeSubscriptionMapper stripeSubscriptionMapper;
    private final QuotaUsageMapper quotaUsageMapper;

    @Override
    public Optional<StripeCustomer> findCustomerByUserId(Long userId) {
        LambdaQueryWrapper<StripeCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeCustomer::getUserId, userId);
        return Optional.ofNullable(stripeCustomerMapper.selectOne(wrapper));
    }

    @Override
    public StripeCustomer findCustomerByUserIdAndNotDeleted(Long userId) {
        LambdaQueryWrapper<StripeCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeCustomer::getUserId, userId)
               .eq(StripeCustomer::getDeleted, 0);
        return stripeCustomerMapper.selectOne(wrapper);
    }

    @Override
    public void saveCustomer(StripeCustomer customer) {
        stripeCustomerMapper.insert(customer);
    }

    @Override
    public void updateCustomer(StripeCustomer customer) {
        stripeCustomerMapper.updateById(customer);
    }

    @Override
    public Optional<StripeSubscription> findSubscriptionByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId);
        return Optional.ofNullable(stripeSubscriptionMapper.selectOne(wrapper));
    }

    @Override
    public StripeSubscription findActiveSubscriptionByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId)
               .eq(StripeSubscription::getDeleted, 0)
               .in(StripeSubscription::getStatus, "active", "trialing")
               .last("LIMIT 1");
        return stripeSubscriptionMapper.selectOne(wrapper);
    }

    @Override
    public StripeSubscription findSubscriptionByStripeId(String stripeSubscriptionId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getStripeSubscriptionId, stripeSubscriptionId);
        return stripeSubscriptionMapper.selectOne(wrapper);
    }

    @Override
    public List<StripeSubscription> findSubscriptionsByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId)
               .eq(StripeSubscription::getDeleted, 0)
               .orderByDesc(StripeSubscription::getCreateTime)
               .last("LIMIT 1");
        return stripeSubscriptionMapper.selectList(wrapper);
    }

    @Override
    public void saveSubscription(StripeSubscription subscription) {
        stripeSubscriptionMapper.insert(subscription);
    }

    @Override
    public void updateSubscription(StripeSubscription subscription) {
        stripeSubscriptionMapper.updateById(subscription);
    }

    @Override
    public int updateSubscriptionByWrapper(LambdaUpdateWrapper<StripeSubscription> wrapper) {
        return stripeSubscriptionMapper.update(null, wrapper);
    }

    @Override
    public QuotaUsage findQuotaUsageByUserIdAndDate(Long userId, LocalDate date) {
        return quotaUsageMapper.findByUserIdAndDate(userId, date);
    }

    @Override
    public void incrementQuotaUsage(Long userId, LocalDate date, long chars) {
        quotaUsageMapper.incrementUsage(userId, date, chars);
    }

    @Override
    public void decrementQuotaUsage(Long userId, LocalDate date, long chars) {
        quotaUsageMapper.decrementUsage(userId, date, chars);
    }

    @Override
    public long getMonthlyQuotaUsage(Long userId, LocalDate monthStart) {
        return quotaUsageMapper.getMonthlyUsage(userId, monthStart);
    }

    @Override
    public void saveQuotaUsage(QuotaUsage quotaUsage) {
        quotaUsageMapper.insert(quotaUsage);
    }
}
