package com.yumu.noveltranslator.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 翻译限流配置
 * 从 application.yaml 读取用户级别的翻译限制
 */
@Component
@ConfigurationProperties(prefix = "translation.limit")
@Getter
@Setter
public class TranslationLimitProperties {

    /** 免费用户每日翻译次数限制 */
    private int freeDailyLimit = 500;

    /** 专业用户每日翻译次数限制 */
    private int proDailyLimit = 5000;

    /** Max 用户每日翻译次数限制 */
    private int maxDailyLimit = Integer.MAX_VALUE;

    /** 免费用户最大并发数 */
    private int freeConcurrencyLimit = 1;

    /** 专业用户最大并发数 */
    private int proConcurrencyLimit = 3;

    /** Max 用户最大并发数 */
    private int maxConcurrencyLimit = 5;

    /** 匿名用户最大并发数 */
    private int anonymousConcurrencyLimit = 1;

    // ==================== 字符配额配置 ====================

    /** 免费用户月度字符包 */
    private long freeMonthlyChars = 100_000;

    /** 专业用户月度字符包 */
    private long proMonthlyChars = 500_000;

    /** Max 用户月度字符包 */
    private long maxMonthlyChars = 2_000_000;

    /** 快速模式系数（消耗更少） */
    private double fastModeMultiplier = 0.5;

    /** 专家模式系数 */
    private double expertModeMultiplier = 1.0;

    /** 团队模式系数（消耗更多） */
    private double teamModeMultiplier = 2.0;
}
