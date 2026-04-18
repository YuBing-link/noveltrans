package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 用户配额响应
 */
@Data
public class UserQuotaResponse {
    /**
     * 用户等级：FREE, PRO
     */
    private String userLevel;
    /**
     * 每日翻译限额
     */
    private Integer dailyLimit;
    /**
     * 今日已用次数
     */
    private Integer usedToday;
    /**
     * 剩余次数
     */
    private Integer remaining;
    /**
     * 并发限制
     */
    private Integer concurrencyLimit;
    /**
     * 是否可翻译文档
     */
    private Boolean canTranslateDocument;
}
