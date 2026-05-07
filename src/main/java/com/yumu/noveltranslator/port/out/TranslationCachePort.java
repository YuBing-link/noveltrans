package com.yumu.noveltranslator.port.out;

import java.util.Map;
import java.util.Optional;

public interface TranslationCachePort {
    Optional<String> getCache(String key);
    Optional<String> getCacheByMode(String key, String mode);
    void putCache(String key, String sourceText, String translated, String sourceLang, String targetLang, String mode, String engine);
    void putToMemoryCache(String key, String value);
    void putNullCache(String key);
    Map<String, Object> getCacheStats();
}
