package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 用户统计数据响应
 */
@Data
public class UserStatisticsResponse {
    /**
     * 总翻译次数
     */
    private Integer totalTranslations;
    /**
     * 文本翻译次数
     */
    private Integer textTranslations;
    /**
     * 文档翻译次数
     */
    private Integer documentTranslations;
    /**
     * 总翻译字符数
     */
    private Long totalCharacters;
    /**
     * 总翻译文档数
     */
    private Integer totalDocuments;
    /**
     * 本周翻译次数
     */
    private Integer weekTranslations;
    /**
     * 本月翻译次数
     */
    private Integer monthTranslations;
}
