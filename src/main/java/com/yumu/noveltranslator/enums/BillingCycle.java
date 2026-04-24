package com.yumu.noveltranslator.enums;

/**
 * 计费周期枚举
 */
public enum BillingCycle {
    MONTHLY("monthly"),
    YEARLY("yearly");

    private final String value;

    BillingCycle(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BillingCycle fromValue(String value) {
        for (BillingCycle cycle : values()) {
            if (cycle.value.equalsIgnoreCase(value)) {
                return cycle;
            }
        }
        throw new IllegalArgumentException("Unknown billing cycle: " + value);
    }
}
