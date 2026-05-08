package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;

import java.util.List;

public interface RagTranslationPort {
    RagTranslationResponse searchSimilarWithModes(String sourceText, String targetLang, List<String> allowedModes);
    RagTranslationResponse searchSimilarWithUserAndModes(String sourceText, String targetLang, Long userId, List<String> allowedModes);
    void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId, String translationMode);
    void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine);
    void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId);
    void clearAllTranslationMemory();
}
