package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeCustomer;
import com.yumu.noveltranslator.adapter.out.persistence.entity.StripeSubscription;
import java.util.List;
import java.util.stream.Collectors;

public final class SubscriptionConverter {
    private SubscriptionConverter() {}

    public static com.yumu.noveltranslator.domain.model.StripeCustomer customerToModel(StripeCustomer e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.StripeCustomer();
        m.setId(e.getId()); m.setUserId(e.getUserId());
        m.setStripeCustomerId(e.getStripeCustomerId());
        m.setCreateTime(e.getCreateTime()); m.setUpdateTime(e.getUpdateTime());
        m.setDeleted(e.getDeleted()); return m;
    }
    public static StripeCustomer customerToEntity(com.yumu.noveltranslator.domain.model.StripeCustomer m) {
        if (m == null) return null;
        var e = new StripeCustomer();
        e.setId(m.getId()); e.setUserId(m.getUserId());
        e.setStripeCustomerId(m.getStripeCustomerId());
        e.setCreateTime(m.getCreateTime()); e.setUpdateTime(m.getUpdateTime());
        e.setDeleted(m.getDeleted()); return e;
    }

    public static com.yumu.noveltranslator.domain.model.StripeSubscription subscriptionToModel(StripeSubscription e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.StripeSubscription();
        m.setId(e.getId()); m.setUserId(e.getUserId());
        m.setStripeCustomerId(e.getStripeCustomerId()); m.setStripeSubscriptionId(e.getStripeSubscriptionId());
        m.setPlan(e.getPlan()); m.setStatus(e.getStatus());
        m.setStripePriceId(e.getStripePriceId()); m.setBillingCycle(e.getBillingCycle());
        m.setCurrentPeriodStart(e.getCurrentPeriodStart()); m.setCurrentPeriodEnd(e.getCurrentPeriodEnd());
        m.setCancelAtPeriodEnd(e.getCancelAtPeriodEnd()); m.setCanceledAt(e.getCanceledAt());
        m.setLastWebhookEventId(e.getLastWebhookEventId()); m.setLastEventCreated(e.getLastEventCreated());
        m.setLastOperationSource(e.getLastOperationSource());
        m.setCreateTime(e.getCreateTime()); m.setUpdateTime(e.getUpdateTime());
        m.setDeleted(e.getDeleted()); return m;
    }
    public static StripeSubscription subscriptionToEntity(com.yumu.noveltranslator.domain.model.StripeSubscription m) {
        if (m == null) return null;
        var e = new StripeSubscription();
        e.setId(m.getId()); e.setUserId(m.getUserId());
        e.setStripeCustomerId(m.getStripeCustomerId()); e.setStripeSubscriptionId(m.getStripeSubscriptionId());
        e.setPlan(m.getPlan()); e.setStatus(m.getStatus());
        e.setStripePriceId(m.getStripePriceId()); e.setBillingCycle(m.getBillingCycle());
        e.setCurrentPeriodStart(m.getCurrentPeriodStart()); e.setCurrentPeriodEnd(m.getCurrentPeriodEnd());
        e.setCancelAtPeriodEnd(m.getCancelAtPeriodEnd()); e.setCanceledAt(m.getCanceledAt());
        e.setLastWebhookEventId(m.getLastWebhookEventId()); e.setLastEventCreated(m.getLastEventCreated());
        e.setLastOperationSource(m.getLastOperationSource());
        e.setCreateTime(m.getCreateTime()); e.setUpdateTime(m.getUpdateTime());
        e.setDeleted(m.getDeleted()); return e;
    }

    // === Aliases for overloaded dispatch ===

    public static com.yumu.noveltranslator.domain.model.StripeCustomer toModel(StripeCustomer e) {
        return customerToModel(e);
    }
    public static StripeCustomer toEntity(com.yumu.noveltranslator.domain.model.StripeCustomer m) {
        return customerToEntity(m);
    }
    public static com.yumu.noveltranslator.domain.model.StripeSubscription toModel(StripeSubscription e) {
        return subscriptionToModel(e);
    }
    public static StripeSubscription toEntity(com.yumu.noveltranslator.domain.model.StripeSubscription m) {
        return subscriptionToEntity(m);
    }

    public static List<com.yumu.noveltranslator.domain.model.StripeCustomer> toModelListCustomer(List<StripeCustomer> l) {
        if (l == null) return null; return l.stream().map(SubscriptionConverter::customerToModel).collect(Collectors.toList());
    }
    public static List<com.yumu.noveltranslator.domain.model.StripeSubscription> toModelListSubscription(List<StripeSubscription> l) {
        if (l == null) return null; return l.stream().map(SubscriptionConverter::subscriptionToModel).collect(Collectors.toList());
    }
}
