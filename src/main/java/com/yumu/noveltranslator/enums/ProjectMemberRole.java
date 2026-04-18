package com.yumu.noveltranslator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 项目成员角色枚举，按权限层级排序：OWNER > REVIEWER > TRANSLATOR
 */
public enum ProjectMemberRole {

    OWNER("OWNER", 3),
    REVIEWER("REVIEWER", 2),
    TRANSLATOR("TRANSLATOR", 1);

    @JsonValue
    private final String value;
    private final int level;

    ProjectMemberRole(String value, int level) {
        this.value = value;
        this.level = level;
    }

    public String getValue() {
        return value;
    }

    /**
     * 判断当前角色是否满足最低角色要求
     */
    public boolean satisfies(ProjectMemberRole minRole) {
        return this.level >= minRole.level;
    }

    public static ProjectMemberRole fromValue(String value) {
        for (ProjectMemberRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown ProjectMemberRole: " + value);
    }
}
