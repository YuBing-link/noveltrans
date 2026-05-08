package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.Glossary;

import java.util.List;

public interface TeamTranslationPort {
    String translateChapter(String text, String novelType, String sourceLang, String targetLang, List<Glossary> glossaryTerms);
}
