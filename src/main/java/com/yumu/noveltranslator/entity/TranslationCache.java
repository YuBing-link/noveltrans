package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 翻译缓存实体
 */
@Data
@TableName("translation_cache")
public class TranslationCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 缓存键（原文 Hash+ 源语言 + 目标语言 + 引擎）
     */
    private String cacheKey;

    /**
     * 原文内容
     */
    private String sourceText;

    /**
     * 翻译后的内容
     */
    private String targetText;

    /**
     * 源语言
     */
    private String sourceLang;

    /**
     * 目标语言
     */
    private String targetLang;

    /**
     * 使用的翻译引擎
     */
    private String engine;

    /**
     * 翻译模式（fast / expert / team）
     */
    private String mode;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
