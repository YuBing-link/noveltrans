package com.yumu.noveltranslator.enums;

import java.util.Set;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 协作项目状态枚举
 * 状态转移矩阵：
 *   DRAFT    → {ACTIVE}
 *   ACTIVE   → {COMPLETED, DRAFT}
 *   COMPLETED→ {ARCHIVED}
 *   ARCHIVED → {ACTIVE}
 */
public enum CollabProjectStatus {

    DRAFT("DRAFT"),
    ACTIVE("ACTIVE"),
    COMPLETED("COMPLETED"),
    ARCHIVED("ARCHIVED");

    private static final Map<CollabProjectStatus, Set<CollabProjectStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE),
            ACTIVE, Set.of(COMPLETED, DRAFT),
            COMPLETED, Set.of(ARCHIVED),
            ARCHIVED, Set.of(ACTIVE)
    );

    @JsonValue
    private final String value;

    CollabProjectStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean canTransitionTo(CollabProjectStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public static CollabProjectStatus fromValue(String value) {
        for (CollabProjectStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CollabProjectStatus: " + value);
    }
}
