package com.yumu.noveltranslator.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 翻译任务通用状态枚举
 * 适用于 Document 和 TranslationTask
 */
public enum TranslationStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    @EnumValue
    private final String value;

    TranslationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static TranslationStatus fromValue(String value) {
        for (TranslationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TranslationStatus: " + value);
    }
}
