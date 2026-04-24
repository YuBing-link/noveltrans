package com.yumu.noveltranslator.enums;

/**
 * 订阅套餐类型枚举
 */
public enum SubscriptionPlan {
    PRO("PRO"),
    MAX("MAX");

    private final String value;

    SubscriptionPlan(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SubscriptionPlan fromValue(String value) {
        for (SubscriptionPlan plan : values()) {
            if (plan.value.equalsIgnoreCase(value)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Unknown subscription plan: " + value);
    }
}
