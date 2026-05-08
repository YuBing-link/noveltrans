package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.converter.QuotaConverter;
import com.yumu.noveltranslator.adapter.out.persistence.converter.SubscriptionConverter;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BillingRepositoryAdapter implements BillingRepositoryPort {

    private final StripeCustomerMapper stripeCustomerMapper;
    private final StripeSubscriptionMapper stripeSubscriptionMapper;
    private final QuotaUsageMapper quotaUsageMapper;

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.StripeCustomer> findCustomerByUserId(Long userId) {
        LambdaQueryWrapper<StripeCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeCustomer::getUserId, userId);
        return Optional.ofNullable(SubscriptionConverter.toModel(stripeCustomerMapper.selectOne(wrapper)));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.StripeCustomer findCustomerByUserIdAndNotDeleted(Long userId) {
        LambdaQueryWrapper<StripeCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeCustomer::getUserId, userId)
               .eq(StripeCustomer::getDeleted, 0);
        return SubscriptionConverter.toModel(stripeCustomerMapper.selectOne(wrapper));
    }

    @Override
    public void saveCustomer(com.yumu.noveltranslator.domain.model.StripeCustomer customer) {
        stripeCustomerMapper.insert(SubscriptionConverter.toEntity(customer));
    }

    @Override
    public void updateCustomer(com.yumu.noveltranslator.domain.model.StripeCustomer customer) {
        stripeCustomerMapper.updateById(SubscriptionConverter.toEntity(customer));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.StripeSubscription> findSubscriptionByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId);
        return Optional.ofNullable(SubscriptionConverter.toModel(stripeSubscriptionMapper.selectOne(wrapper)));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.StripeSubscription findActiveSubscriptionByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId)
               .eq(StripeSubscription::getDeleted, 0)
               .in(StripeSubscription::getStatus, "active", "trialing")
               .last("LIMIT 1");
        return SubscriptionConverter.toModel(stripeSubscriptionMapper.selectOne(wrapper));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.StripeSubscription findSubscriptionByStripeId(String stripeSubscriptionId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getStripeSubscriptionId, stripeSubscriptionId);
        return SubscriptionConverter.toModel(stripeSubscriptionMapper.selectOne(wrapper));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.StripeSubscription> findSubscriptionsByUserId(Long userId) {
        LambdaQueryWrapper<StripeSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StripeSubscription::getUserId, userId)
               .eq(StripeSubscription::getDeleted, 0)
               .orderByDesc(StripeSubscription::getCreateTime)
               .last("LIMIT 1");
        return stripeSubscriptionMapper.selectList(wrapper).stream()
                .map(SubscriptionConverter::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public void saveSubscription(com.yumu.noveltranslator.domain.model.StripeSubscription subscription) {
        stripeSubscriptionMapper.insert(SubscriptionConverter.toEntity(subscription));
    }

    @Override
    public void updateSubscription(com.yumu.noveltranslator.domain.model.StripeSubscription subscription) {
        stripeSubscriptionMapper.updateById(SubscriptionConverter.toEntity(subscription));
    }

    @Override
    public int claimWebhookEvent(Long id, String eventId) {
        LambdaUpdateWrapper<StripeSubscription> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StripeSubscription::getId, id)
               .isNull(StripeSubscription::getLastWebhookEventId)
               .set(StripeSubscription::getLastWebhookEventId, eventId);
        return stripeSubscriptionMapper.update(null, wrapper);
    }

    @Override
    public int atomicUpdateSubscription(Long id, String eventId, String status, Long eventCreated) {
        LambdaUpdateWrapper<StripeSubscription> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StripeSubscription::getId, id)
               .and(w -> w
                   .isNull(StripeSubscription::getLastWebhookEventId)
                   .or()
                   .ne(StripeSubscription::getLastWebhookEventId, eventId))
               .and(w -> w
                   .isNull(StripeSubscription::getLastEventCreated)
                   .or()
                   .lt(StripeSubscription::getLastEventCreated, eventCreated))
               .set(StripeSubscription::getStatus, status)
               .set(StripeSubscription::getLastWebhookEventId, eventId)
               .set(StripeSubscription::getLastEventCreated, eventCreated);
        return stripeSubscriptionMapper.update(null, wrapper);
    }

    @Override
    public void updateSubscriptionFields(Long id, java.time.LocalDateTime periodStart, java.time.LocalDateTime periodEnd, java.time.LocalDateTime canceledAt) {
        LambdaUpdateWrapper<StripeSubscription> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StripeSubscription::getId, id);
        if (periodStart != null) wrapper.set(StripeSubscription::getCurrentPeriodStart, periodStart);
        if (periodEnd != null) wrapper.set(StripeSubscription::getCurrentPeriodEnd, periodEnd);
        if (canceledAt != null) wrapper.set(StripeSubscription::getCanceledAt, canceledAt);
        stripeSubscriptionMapper.update(null, wrapper);
    }

    @Override
    public com.yumu.noveltranslator.domain.model.QuotaUsage findQuotaUsageByUserIdAndDate(Long userId, LocalDate date) {
        return QuotaConverter.toModel(quotaUsageMapper.findByUserIdAndDate(userId, date));
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
    public void saveQuotaUsage(com.yumu.noveltranslator.domain.model.QuotaUsage quotaUsage) {
        quotaUsageMapper.insert(QuotaConverter.toEntity(quotaUsage));
    }
}
