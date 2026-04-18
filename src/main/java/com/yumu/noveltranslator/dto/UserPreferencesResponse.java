package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 用户偏好设置响应
 */
@Data
public class UserPreferencesResponse {
    /**
     * 默认翻译引擎
     */
    private String defaultEngine;

    /**
     * 默认目标语言
     */
    private String defaultTargetLang;

    /**
     * 是否启用术语库
     */
    private Boolean enableGlossary;

    /**
     * 默认术语库 ID
     */
    private Integer defaultGlossaryId;

    /**
     * 是否启用缓存
     */
    private Boolean enableCache;

    /**
     * 是否自动翻译选中内容
     */
    private Boolean autoTranslateSelection;

    /**
     * 字体大小
     */
    private Integer fontSize;

    /**
     * 主题模式（light/dark/auto）
     */
    private String themeMode;
}
