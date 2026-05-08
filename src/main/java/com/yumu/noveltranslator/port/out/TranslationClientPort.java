package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.Glossary;

import java.util.List;

public interface TranslationClientPort {
    String translate(String text, String targetLang, String engine, boolean html, boolean fastMode, List<Glossary> glossaryTerms, String userId, String userLevel);
}
