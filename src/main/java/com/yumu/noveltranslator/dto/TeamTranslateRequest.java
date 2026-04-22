package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 翻译团队请求 DTO
 */
@Data
public class TeamTranslateRequest {

    /**
     * 待翻译的原文
     */
    private String text;

    /**
     * 小说类型（如 fantasy, romance, scifi 等）
     */
    private String novelType;

    /**
     * 源语言
     */
    private String sourceLang;

    /**
     * 目标语言
     */
    private String targetLang;

    /**
     * 术语表词条列表
     */
    private List<GlossaryTerm> glossaryTerms;

    /**
     * 占位符映射 (可选)。用于实体一致性翻译：原文中的实体被替换为 [{1}], [{2}] 等占位符
     * 格式: {"[{1}]": "翻译后的实体名", "[{2}]": "..."}
     */
    private Map<String, String> placeholders;

    /**
     * 术语表词条（内部类）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlossaryTerm {

        /**
         * 原文词条
         */
        private String source;

        /**
         * 译文词条
         */
        private String target;

        /**
         * 备注说明
         */
        private String note;
    }
}
