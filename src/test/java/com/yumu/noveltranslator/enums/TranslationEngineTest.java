package com.yumu.noveltranslator.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationEngineTest {

    @Test
    void getMaxCharsForGoogle() {
        assertEquals(1000, TranslationEngine.getMaxChars("google"));
        assertEquals(1000, TranslationEngine.getMaxChars("GOOGLE"));
        assertEquals(1000, TranslationEngine.getMaxChars("Google"));
    }

    @Test
    void getMaxCharsForDeepL() {
        assertEquals(3000, TranslationEngine.getMaxChars("deepl"));
    }

    @Test
    void getMaxCharsForMyMemory() {
        assertEquals(450, TranslationEngine.getMaxChars("mymemory"));
    }

    @Test
    void getMaxCharsForBaidu() {
        assertEquals(2000, TranslationEngine.getMaxChars("baidu"));
    }

    @Test
    void getMaxCharsForLibre() {
        assertEquals(1000, TranslationEngine.getMaxChars("libre"));
    }

    @Test
    void getMaxCharsForOpenAI() {
        assertEquals(2000, TranslationEngine.getMaxChars("openai"));
    }

    @Test
    void getMaxCharsForYoudao() {
        assertEquals(2000, TranslationEngine.getMaxChars("youdao"));
    }

    @Test
    void getMaxCharsWithNullReturnsDefault() {
        assertEquals(1000, TranslationEngine.getMaxChars(null));
    }

    @Test
    void getMaxCharsWithAutoReturnsDefault() {
        assertEquals(1000, TranslationEngine.getMaxChars("auto"));
        assertEquals(1000, TranslationEngine.getMaxChars("AUTO"));
    }

    @Test
    void getMaxCharsWithUnknownEngineReturnsDefault() {
        assertEquals(1000, TranslationEngine.getMaxChars("unknown_engine"));
    }
}
