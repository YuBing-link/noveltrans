package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.enums.TranslationMode;

import java.util.Map;

/**
 * 前端引擎别名注册表
 *
 * <p>将前端传入的引擎名称（如 google、ai、fast、deepl 等）归一化为翻译质量档位（FAST/EXPERT/TEAM）。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>前端引擎名是"用户视角"的翻译偏好，不代表实际后端实现</li>
 *   <li>归一化为 TranslationMode 后，用于决定缓存层级和后端路由</li>
 *   <li>未知或空引擎名默认归一化为 FAST</li>
 * </ul>
 */
public class EngineAliasRegistry {

    /**
     * 前端引擎名 → TranslationMode 映射
     *
     * 分类说明：
     * - 快速类：google, mtran, fast, libre, youdao, baidu → FAST（传统机器翻译档位）
     * - 专家类：ai, openai, deepl, deepseek → EXPERT（LLM 高质量翻译）
     * - 团队类：ai-team, team → TEAM（多 Agent 协作）
     */
    private static final Map<String, TranslationMode> ALIAS_TO_MODE = Map.ofEntries(
            // 快速类引擎（免费/传统翻译服务）
            Map.entry("google", TranslationMode.FAST),
            Map.entry("mtran", TranslationMode.FAST),
            Map.entry("fast", TranslationMode.FAST),
            Map.entry("libre", TranslationMode.FAST),
            Map.entry("youdao", TranslationMode.FAST),
            Map.entry("baidu", TranslationMode.FAST),
            Map.entry("mymemory", TranslationMode.FAST),

            // 专家类引擎（LLM 高质量翻译）
            Map.entry("ai", TranslationMode.EXPERT),
            Map.entry("openai", TranslationMode.EXPERT),
            Map.entry("deepl", TranslationMode.EXPERT),
            Map.entry("deepseek", TranslationMode.EXPERT),

            // 团队类引擎（多 Agent 协作）
            Map.entry("ai-team", TranslationMode.TEAM),
            Map.entry("team", TranslationMode.TEAM)
    );

    /**
     * 默认翻译模式（未知引擎时降级）
     */
    private static final TranslationMode DEFAULT_MODE = TranslationMode.FAST;

    private EngineAliasRegistry() {
    }

    /**
     * 将前端引擎名归一化为翻译质量档位
     *
     * @param engineName 前端传入的引擎名称（大小写不敏感）
     * @return 对应的 TranslationMode，未知名称返回 FAST
     */
    public static TranslationMode normalizeToMode(String engineName) {
        if (engineName == null || engineName.isBlank()) {
            return DEFAULT_MODE;
        }
        return ALIAS_TO_MODE.getOrDefault(engineName.trim().toLowerCase(), DEFAULT_MODE);
    }
}
