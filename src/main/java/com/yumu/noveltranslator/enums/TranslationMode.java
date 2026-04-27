package com.yumu.noveltranslator.enums;

import java.util.List;
import java.util.Map;

/**
 * 翻译质量档位枚举
 *
 * <p>三档翻译模式：</p>
 * <ul>
 *   <li>FAST - 快速翻译，走 MTranServer，缓存可读取 team/expert/fast</li>
 *   <li>EXPERT - 专家翻译，走 Python LLM 服务，缓存可读取 team/expert</li>
 *   <li>TEAM - 团队翻译，走多 Agent 协作，缓存仅读取 team</li>
 * </ul>
 */
public enum TranslationMode {

    /** 快速模式：MTranServer 直译，缓存层级最低，可读取更高级缓存 */
    FAST("fast", List.of("team", "expert", "fast"), false),

    /** 专家模式：Python LLM 翻译，缓存可读取 team/expert */
    EXPERT("expert", List.of("team", "expert"), false),

    /** 团队模式：多 Agent 协作翻译，缓存仅读取 team */
    TEAM("team", List.of("team"), true);

    /** 模式名称（用于缓存后缀和日志） */
    private final String name;

    /** 缓存读取层级：按顺序搜索，命中即返回 */
    private final List<String> cacheHierarchy;

    /** 是否走 TeamTranslationService */
    private final boolean teamMode;

    /** 模式名称 → 枚举的映射，用于快速查找 */
    private static final Map<String, TranslationMode> BY_NAME = Map.of(
            "fast", FAST,
            "expert", EXPERT,
            "team", TEAM
    );

    TranslationMode(String name, List<String> cacheHierarchy, boolean teamMode) {
        this.name = name;
        this.cacheHierarchy = cacheHierarchy;
        this.teamMode = teamMode;
    }

    /**
     * 获取模式名称（小写字符串形式）
     */
    public String getName() {
        return name;
    }

    /**
     * 获取缓存层级列表
     */
    public List<String> getCacheHierarchy() {
        return cacheHierarchy;
    }

    /**
     * 是否为团队模式
     */
    public boolean isTeamMode() {
        return teamMode;
    }

    /**
     * 根据模式名称字符串查找枚举
     * @param name 模式名称（大小写不敏感）
     * @return 匹配的 TranslationMode，无匹配时返回 FAST
     */
    public static TranslationMode fromName(String name) {
        if (name == null || name.isBlank()) {
            return FAST;
        }
        return BY_NAME.getOrDefault(name.trim().toLowerCase(), FAST);
    }
}
