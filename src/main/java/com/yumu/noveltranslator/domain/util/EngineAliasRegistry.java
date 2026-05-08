package com.yumu.noveltranslator.domain.util;

import com.yumu.noveltranslator.enums.TranslationMode;

import java.util.Map;

/**
 * 前端引擎别名注册表
 *
 * <p>将前端传入的引擎名称（如 google、ai、fast、deepl 等）归一化为翻译质量档位（FAST/EXPERT/TEAM）。</p>
 */
public class EngineAliasRegistry {

    private static final Map<String, TranslationMode> ALIAS_TO_MODE = Map.ofEntries(
            Map.entry("google", TranslationMode.FAST),
            Map.entry("mtran", TranslationMode.FAST),
            Map.entry("fast", TranslationMode.FAST),
            Map.entry("libre", TranslationMode.FAST),
            Map.entry("youdao", TranslationMode.FAST),
            Map.entry("baidu", TranslationMode.FAST),
            Map.entry("mymemory", TranslationMode.FAST),
            Map.entry("ai", TranslationMode.EXPERT),
            Map.entry("openai", TranslationMode.EXPERT),
            Map.entry("deepl", TranslationMode.EXPERT),
            Map.entry("deepseek", TranslationMode.EXPERT),
            Map.entry("ai-team", TranslationMode.TEAM),
            Map.entry("team", TranslationMode.TEAM)
    );

    private static final TranslationMode DEFAULT_MODE = TranslationMode.FAST;

    private EngineAliasRegistry() {
    }

    public static TranslationMode normalizeToMode(String engineName) {
        if (engineName == null || engineName.isBlank()) {
            return DEFAULT_MODE;
        }
        return ALIAS_TO_MODE.getOrDefault(engineName.trim().toLowerCase(), DEFAULT_MODE);
    }
}
