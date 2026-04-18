package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 平台统计响应
 */
@Data
public class PlatformStatsResponse {
    /**
     * 总用户数
     */
    private Integer totalUsers;

    /**
     * 今日活跃用户数
     */
    private Integer activeUsersToday;

    /**
     * 本周活跃用户数
     */
    private Integer activeUsersWeek;

    /**
     * 本月活跃用户数
     */
    private Integer activeUsersMonth;

    /**
     * 总翻译次数
     */
    private Long totalTranslations;

    /**
     * 今日翻译次数
     */
    private Long translationsToday;

    /**
     * 总翻译字符数
     */
    private Long totalCharacters;

    /**
     * 文档翻译总数
     */
    private Integer totalDocumentTranslations;

    /**
     * 术语库总数
     */
    private Integer totalGlossaries;

    /**
     * 系统状态（normal/maintenance）
     */
    private String systemStatus;
}
