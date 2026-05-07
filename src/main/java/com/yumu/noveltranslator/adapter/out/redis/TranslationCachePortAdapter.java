package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.TranslationCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TranslationCachePortAdapter implements TranslationCachePort {

    private final TranslationCacheService cacheService;

    @Override
    public Optional<String> getCache(String key) {
        return Optional.ofNullable(cacheService.getCache(key));
    }

    @Override
    public Optional<String> getCacheByMode(String key, String mode) {
        return Optional.ofNullable(cacheService.getCacheByMode(key, mode));
    }

    @Override
    public void putCache(String key, String sourceText, String translated, String sourceLang, String targetLang, String mode, String engine) {
        cacheService.putCache(key, sourceText, translated, sourceLang, targetLang, engine, mode);
    }

    @Override
    public void putToMemoryCache(String key, String value) {
        cacheService.putToMemoryCache(key, value);
    }

    @Override
    public void putNullCache(String key) {
        cacheService.putNullCache(key);
    }

    @Override
    public Map<String, Object> getCacheStats() {
        return cacheService.getCacheStats();
    }
}
