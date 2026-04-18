package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;

/**
 * 实体一致性翻译结果
 */
@Data
public class ConsistencyTranslationResult {
    /** 最终翻译后的文本（占位符已还原） */
    private String translatedText;
    /** 实体映射列表 */
    private List<EntityMapping> mappings;
    /** 替换了占位符的原文（调试用） */
    private String originalWithPlaceholders;
    /** 是否实际启用了实体一致性（短文本可能返回 false） */
    private boolean consistencyApplied;
}
