package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;

import java.util.List;

public interface RagTranslationPort {
    RagTranslationResponse searchSimilarWithModes(Long userId, String sourceText, String targetLang, List<String> allowedModes);
    void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId, String translationMode);
    void clearAllTranslationMemory();
}
