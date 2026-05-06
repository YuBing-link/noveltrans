package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.enums.TranslationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EngineAliasRegistry 测试")
class EngineAliasRegistryTest {

    @Test
    void null引擎名返回FAST() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode(null));
    }

    @Test
    void 空白引擎名返回FAST() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode(""));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("   "));
    }

    @Test
    void 未知引擎名返回FAST() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("unknown"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("custom_engine"));
    }

    @Test
    void 快速类引擎归一化为FAST() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("google"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("mtran"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("fast"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("libre"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("youdao"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("baidu"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("mymemory"));
    }

    @Test
    void 专家类引擎归一化为EXPERT() {
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("ai"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("openai"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("deepl"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("deepseek"));
    }

    @Test
    void 团队类引擎归一化为TEAM() {
        assertEquals(TranslationMode.TEAM, EngineAliasRegistry.normalizeToMode("ai-team"));
        assertEquals(TranslationMode.TEAM, EngineAliasRegistry.normalizeToMode("team"));
    }

    @Test
    void 大小写不敏感() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("Google"));
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode("GOOGLE"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("AI"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("OpenAI"));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode("DeepL"));
        assertEquals(TranslationMode.TEAM, EngineAliasRegistry.normalizeToMode("AI-Team"));
        assertEquals(TranslationMode.TEAM, EngineAliasRegistry.normalizeToMode("Team"));
    }

    @Test
    void 前后空白自动trim() {
        assertEquals(TranslationMode.FAST, EngineAliasRegistry.normalizeToMode(" google "));
        assertEquals(TranslationMode.EXPERT, EngineAliasRegistry.normalizeToMode(" ai "));
        assertEquals(TranslationMode.TEAM, EngineAliasRegistry.normalizeToMode(" team "));
    }
}
