package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.AiGlossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ChapterEntityMap;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;

public final class GlossaryConverter {

    private GlossaryConverter() {}

    // === Glossary ===

    public static com.yumu.noveltranslator.domain.model.Glossary glossaryToModel(Glossary e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.Glossary();
        m.setId(e.getId());
        m.setUserId(e.getUserId());
        m.setSourceWord(e.getSourceWord());
        m.setTargetWord(e.getTargetWord());
        m.setRemark(e.getRemark());
        m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime());
        m.setDeleted(e.getDeleted());
        return m;
    }

    public static Glossary glossaryToEntity(com.yumu.noveltranslator.domain.model.Glossary m) {
        if (m == null) return null;
        var e = new Glossary();
        e.setId(m.getId());
        e.setUserId(m.getUserId());
        e.setSourceWord(m.getSourceWord());
        e.setTargetWord(m.getTargetWord());
        e.setRemark(m.getRemark());
        e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime());
        e.setDeleted(m.getDeleted());
        return e;
    }

    // === AiGlossary ===

    public static com.yumu.noveltranslator.domain.model.AiGlossary aiGlossaryToModel(AiGlossary e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.AiGlossary();
        m.setId(e.getId());
        m.setProjectId(e.getProjectId());
        m.setSourceWord(e.getSourceWord());
        m.setTargetWord(e.getTargetWord());
        m.setContext(e.getContext());
        m.setEntityType(e.getEntityType());
        m.setChapterId(e.getChapterId());
        m.setConfidence(e.getConfidence());
        m.setStatus(e.getStatus());
        m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime());
        return m;
    }

    public static AiGlossary aiGlossaryToEntity(com.yumu.noveltranslator.domain.model.AiGlossary m) {
        if (m == null) return null;
        var e = new AiGlossary();
        e.setId(m.getId());
        e.setProjectId(m.getProjectId());
        e.setSourceWord(m.getSourceWord());
        e.setTargetWord(m.getTargetWord());
        e.setContext(m.getContext());
        e.setEntityType(m.getEntityType());
        e.setChapterId(m.getChapterId());
        e.setConfidence(m.getConfidence());
        e.setStatus(m.getStatus());
        e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime());
        return e;
    }

    // === ChapterEntityMap ===

    public static com.yumu.noveltranslator.domain.model.ChapterEntityMap chapterMapToModel(ChapterEntityMap e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.ChapterEntityMap();
        m.setId(e.getId());
        m.setChapterId(e.getChapterId());
        m.setProjectId(e.getProjectId());
        m.setSourceEntity(e.getSourceEntity());
        m.setTargetEntity(e.getTargetEntity());
        m.setEntityType(e.getEntityType());
        m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime());
        return m;
    }

    public static ChapterEntityMap chapterMapToEntity(com.yumu.noveltranslator.domain.model.ChapterEntityMap m) {
        if (m == null) return null;
        var e = new ChapterEntityMap();
        e.setId(m.getId());
        e.setChapterId(m.getChapterId());
        e.setProjectId(m.getProjectId());
        e.setSourceEntity(m.getSourceEntity());
        e.setTargetEntity(m.getTargetEntity());
        e.setEntityType(m.getEntityType());
        e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime());
        return e;
    }

    // === TranslationMemory ===

    public static com.yumu.noveltranslator.domain.model.TranslationMemory translationMemoryToModel(TranslationMemory e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.TranslationMemory();
        m.setId(e.getId());
        m.setUserId(e.getUserId());
        m.setProjectId(e.getProjectId());
        m.setSourceLang(e.getSourceLang());
        m.setTargetLang(e.getTargetLang());
        m.setSourceText(e.getSourceText());
        m.setTargetText(e.getTargetText());
        m.setEmbedding(e.getEmbedding());
        m.setUsageCount(e.getUsageCount());
        m.setSourceEngine(e.getSourceEngine());
        m.setTranslationMode(e.getTranslationMode());
        m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime());
        m.setDeleted(e.getDeleted());
        return m;
    }

    public static TranslationMemory translationMemoryToEntity(com.yumu.noveltranslator.domain.model.TranslationMemory m) {
        if (m == null) return null;
        var e = new TranslationMemory();
        e.setId(m.getId());
        e.setUserId(m.getUserId());
        e.setProjectId(m.getProjectId());
        e.setSourceLang(m.getSourceLang());
        e.setTargetLang(m.getTargetLang());
        e.setSourceText(m.getSourceText());
        e.setTargetText(m.getTargetText());
        e.setEmbedding(m.getEmbedding());
        e.setUsageCount(m.getUsageCount());
        e.setSourceEngine(m.getSourceEngine());
        e.setTranslationMode(m.getTranslationMode());
        e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime());
        e.setDeleted(m.getDeleted());
        return e;
    }
}
