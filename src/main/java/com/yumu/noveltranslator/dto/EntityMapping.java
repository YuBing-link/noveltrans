package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体映射表项：原文实体 → 译文 → 占位符
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMapping {
    /** 原文中的实体名 */
    private String sourceText;
    /** 翻译后的实体名 */
    private String translatedText;
    /** 占位符，如 [{1}] */
    private String placeholder;
    /** 索引编号 */
    private int index;
}
