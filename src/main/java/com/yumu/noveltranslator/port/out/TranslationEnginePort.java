package com.yumu.noveltranslator.port.out;

public interface TranslationEnginePort {
    String translate(String text, String sourceLang, String targetLang, String engine);
    boolean isHealthy(String engine);
}
