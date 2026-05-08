package com.yumu.noveltranslator.port.out;

public record VectorDocument(
    String sourceText,
    String targetText,
    String sourceLang,
    String targetLang,
    Long userId,
    float[] embedding,
    String translationMode
) {}
