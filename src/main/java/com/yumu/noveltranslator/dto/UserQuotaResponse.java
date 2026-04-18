package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 用户字符配额响应
 */
@Data
public class UserQuotaResponse {
    /**
     * 用户等级：FREE, PRO, MAX
     */
    private String userLevel;
    /**
     * 月度字符包
     */
    private Long monthlyChars;
    /**
     * 本月已用字符
     */
    private Long usedThisMonth;
    /**
     * 剩余字符
     */
    private Long remainingChars;
    /**
     * 并发限制
     */
    private Integer concurrencyLimit;
    /**
     * 快速模式等效原文字符（×0.5）
     */
    private Long fastModeEquivalent;
    /**
     * 专家模式等效原文字符（×1.0）
     */
    private Long expertModeEquivalent;
    /**
     * 团队模式等效原文字符（×2.0）
     */
    private Long teamModeEquivalent;
}
