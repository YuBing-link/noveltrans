package com.yumu.noveltranslator.enums;

/**
 * 翻译引擎配置枚举
 * 与 Python 端 translate_server.py 中的 ENGINE_REGISTRY 保持一致
 *
 * Python 端支持的引擎：
 * - google (free, priority 1)
 * - mymemory (free, priority 2)
 * - libre (free, priority 3)
 * - baidu (requires API key, priority 4)
 * - deepl (requires API key, priority 5)
 */
public enum TranslationEngine {
    GOOGLE(1000),      // Google 建议限制
    MYMEMORY(450),     // MyMemory 限制严格
    LIBRE(1000),       // Libre 翻译
    BAIDU(2000),       // 百度翻译
    DEEPL(3000),       // DeepL 容忍度高
    OPENAI(2000),      // GPT 系列建议
    YOUDAO(2000);      // 有道翻译

    // 定义默认引擎（便于后续统一修改）
    private static final TranslationEngine DEFAULT_ENGINE = GOOGLE;
    private final int maxChars;

    TranslationEngine(int maxChars) {
        this.maxChars = maxChars;
    }

    /**
     * 核心快捷方法：直接根据引擎名称获取最大字符限制
     * 一步到位，无需额外调用其他方法
     * @param engineName 翻译引擎名称（大小写不敏感，如"google"、"DeepL"）
     * @return 对应引擎的最大字符数，无匹配/传入 null 则返回默认引擎 (GOOGLE) 的限制
     */
    public static int getMaxChars(String engineName) {
        // 空值处理
        if (engineName == null) {
            return DEFAULT_ENGINE.maxChars;
        }

        // 特殊处理 "auto" 引擎，返回默认值
        if ("auto".equalsIgnoreCase(engineName)) {
            return DEFAULT_ENGINE.maxChars;
        }

        // 匹配枚举并返回字符数，不匹配则返回默认值
        try {
            return TranslationEngine.valueOf(engineName.toUpperCase()).maxChars;
        } catch (IllegalArgumentException e) {
            return DEFAULT_ENGINE.maxChars;
        }
    }
}
