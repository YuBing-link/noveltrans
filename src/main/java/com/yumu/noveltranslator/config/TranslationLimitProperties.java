package com.yumu.noveltranslator.config;

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

    /**
     * 免费用户每日翻译次数限制
     */
    private int freeDailyLimit = 100;

    /**
     * 专业用户每日翻译次数限制
     */
    private int proDailyLimit = 1000;

    /**
     * 免费用户最大并发数
     */
    private int freeConcurrencyLimit = 5;

    /**
     * 专业用户最大并发数
     */
    private int proConcurrencyLimit = 20;

    /**
     * 匿名用户最大并发数
     */
    private int anonymousConcurrencyLimit = 3;
}
