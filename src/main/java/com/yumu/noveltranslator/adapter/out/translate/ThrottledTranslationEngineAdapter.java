package com.yumu.noveltranslator.adapter.out.translate;

import com.yumu.noveltranslator.port.out.TranslationEnginePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TranslationEnginePort adapter that delegates to UserLevelThrottledTranslationClient.
 * Provides the simple translate(text, sourceLang, targetLang, engine) interface
 * while leveraging the existing rate limiting, round-robin, and fallback logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThrottledTranslationEngineAdapter implements TranslationEnginePort {

    private final UserLevelThrottledTranslationClient throttledClient;

    @Override
    public String translate(String text, String sourceLang, String targetLang, String engine) {
        return throttledClient.translate(text, targetLang, engine, false);
    }

    @Override
    public boolean isHealthy(String engine) {
        return throttledClient != null;
    }
}
