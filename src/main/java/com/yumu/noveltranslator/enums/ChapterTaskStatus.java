package com.yumu.noveltranslator.enums;

import java.util.Set;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 章节任务状态枚举
 * 状态转移矩阵：
 *   UNASSIGNED  → {TRANSLATING, SUBMITTED}
 *   TRANSLATING → {SUBMITTED, UNASSIGNED}
 *   SUBMITTED   → {REVIEWING}
 *   REVIEWING   → {APPROVED, REJECTED}
 *   APPROVED    → {COMPLETED}
 *   REJECTED    → {TRANSLATING}
 *   COMPLETED   → {∅}
 */
public enum ChapterTaskStatus {

    UNASSIGNED("UNASSIGNED"),
    TRANSLATING("TRANSLATING"),
    SUBMITTED("SUBMITTED"),
    REVIEWING("REVIEWING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    COMPLETED("COMPLETED");

    private static final Map<ChapterTaskStatus, Set<ChapterTaskStatus>> VALID_TRANSITIONS = Map.of(
            UNASSIGNED, Set.of(TRANSLATING, SUBMITTED),
            TRANSLATING, Set.of(SUBMITTED, UNASSIGNED),
            SUBMITTED, Set.of(REVIEWING),
            REVIEWING, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(COMPLETED),
            REJECTED, Set.of(TRANSLATING)
    );

    @JsonValue
    private final String value;

    ChapterTaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean canTransitionTo(ChapterTaskStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public static ChapterTaskStatus fromValue(String value) {
        for (ChapterTaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ChapterTaskStatus: " + value);
    }
}
