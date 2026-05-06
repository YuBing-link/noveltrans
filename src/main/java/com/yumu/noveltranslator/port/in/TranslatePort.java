package com.yumu.noveltranslator.port.in;

public interface TranslatePort {
    String translate(String text, String sourceLang, String targetLang, String engine, String mode);
    String translateFast(String text, String sourceLang, String targetLang, String engine);
}
