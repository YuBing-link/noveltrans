package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationCache;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationHistory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationTask;
import java.util.List;
import java.util.stream.Collectors;

public final class TranslationConverter {
    private TranslationConverter() {}

    public static List<com.yumu.noveltranslator.domain.model.Document> toModelDocuments(List<Document> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toModelDocument).collect(Collectors.toList());
    }
    public static List<Document> toEntityDocuments(List<com.yumu.noveltranslator.domain.model.Document> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toEntityDocument).collect(Collectors.toList());
    }

    // === Document (with explicit method names to avoid type erasure conflict) ===
    public static com.yumu.noveltranslator.domain.model.Document toModelDocument(Document e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.Document();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setName(e.getName());
        m.setPath(e.getPath()); m.setSourceLang(e.getSourceLang()); m.setTargetLang(e.getTargetLang());
        m.setFileType(e.getFileType()); m.setFileSize(e.getFileSize()); m.setTaskId(e.getTaskId());
        m.setStatus(e.getStatus()); m.setMode(e.getMode()); m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime()); m.setUpdateTime(e.getUpdateTime());
        m.setCompletedTime(e.getCompletedTime()); m.setErrorMessage(e.getErrorMessage());
        m.setDeleted(e.getDeleted()); return m;
    }
    public static Document toEntityDocument(com.yumu.noveltranslator.domain.model.Document m) {
        if (m == null) return null;
        var e = new Document();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setName(m.getName());
        e.setPath(m.getPath()); e.setSourceLang(m.getSourceLang()); e.setTargetLang(m.getTargetLang());
        e.setFileType(m.getFileType()); e.setFileSize(m.getFileSize()); e.setTaskId(m.getTaskId());
        e.setStatus(m.getStatus()); e.setMode(m.getMode()); e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime()); e.setUpdateTime(m.getUpdateTime());
        e.setCompletedTime(m.getCompletedTime()); e.setErrorMessage(m.getErrorMessage());
        e.setDeleted(m.getDeleted()); return e;
    }

    public static com.yumu.noveltranslator.domain.model.TranslationTask toModelTask(TranslationTask e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.TranslationTask();
        m.setId(e.getId()); m.setTaskId(e.getTaskId()); m.setUserId(e.getUserId());
        m.setType(e.getType()); m.setDocumentId(e.getDocumentId()); m.setSourceLang(e.getSourceLang());
        m.setTargetLang(e.getTargetLang()); m.setMode(e.getMode()); m.setEngine(e.getEngine());
        m.setStatus(e.getStatus()); m.setProgress(e.getProgress()); m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime()); m.setUpdateTime(e.getUpdateTime());
        m.setCompletedTime(e.getCompletedTime()); m.setErrorMessage(e.getErrorMessage());
        m.setDeleted(e.getDeleted()); return m;
    }
    public static TranslationTask toEntityTask(com.yumu.noveltranslator.domain.model.TranslationTask m) {
        if (m == null) return null;
        var e = new TranslationTask();
        e.setId(m.getId()); e.setTaskId(m.getTaskId()); e.setUserId(m.getUserId());
        e.setType(m.getType()); e.setDocumentId(m.getDocumentId()); e.setSourceLang(m.getSourceLang());
        e.setTargetLang(m.getTargetLang()); e.setMode(m.getMode()); e.setEngine(m.getEngine());
        e.setStatus(m.getStatus()); e.setProgress(m.getProgress()); e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime()); e.setUpdateTime(m.getUpdateTime());
        e.setCompletedTime(m.getCompletedTime()); e.setErrorMessage(m.getErrorMessage());
        e.setDeleted(m.getDeleted()); return e;
    }
    public static List<com.yumu.noveltranslator.domain.model.TranslationTask> toModelListTasks(List<TranslationTask> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toModelTask).collect(Collectors.toList());
    }
    public static List<TranslationTask> toEntityListTasks(List<com.yumu.noveltranslator.domain.model.TranslationTask> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toEntityTask).collect(Collectors.toList());
    }

    public static com.yumu.noveltranslator.domain.model.TranslationHistory toModelHistory(TranslationHistory e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.TranslationHistory();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setTaskId(e.getTaskId());
        m.setType(e.getType()); m.setDocumentId(e.getDocumentId()); m.setSourceLang(e.getSourceLang());
        m.setTargetLang(e.getTargetLang()); m.setSourceText(e.getSourceText()); m.setTargetText(e.getTargetText());
        m.setEngine(e.getEngine()); m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime()); m.setDeleted(e.getDeleted()); return m;
    }
    public static TranslationHistory toEntityHistory(com.yumu.noveltranslator.domain.model.TranslationHistory m) {
        if (m == null) return null;
        var e = new TranslationHistory();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setTaskId(m.getTaskId());
        e.setType(m.getType()); e.setDocumentId(m.getDocumentId()); e.setSourceLang(m.getSourceLang());
        e.setTargetLang(m.getTargetLang()); e.setSourceText(m.getSourceText()); e.setTargetText(m.getTargetText());
        e.setEngine(m.getEngine()); e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime()); e.setDeleted(m.getDeleted()); return e;
    }
    public static List<com.yumu.noveltranslator.domain.model.TranslationHistory> toModelListHistory(List<TranslationHistory> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toModelHistory).collect(Collectors.toList());
    }
    public static List<TranslationHistory> toEntityListHistory(List<com.yumu.noveltranslator.domain.model.TranslationHistory> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toEntityHistory).collect(Collectors.toList());
    }

    public static com.yumu.noveltranslator.domain.model.TranslationMemory toModelMemory(TranslationMemory e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.TranslationMemory();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setProjectId(e.getProjectId());
        m.setSourceLang(e.getSourceLang()); m.setTargetLang(e.getTargetLang());
        m.setSourceText(e.getSourceText()); m.setTargetText(e.getTargetText());
        m.setEmbedding(e.getEmbedding()); m.setUsageCount(e.getUsageCount());
        m.setSourceEngine(e.getSourceEngine()); m.setTranslationMode(e.getTranslationMode());
        m.setTenantId(e.getTenantId()); m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime()); m.setDeleted(e.getDeleted()); return m;
    }
    public static TranslationMemory toEntityMemory(com.yumu.noveltranslator.domain.model.TranslationMemory m) {
        if (m == null) return null;
        var e = new TranslationMemory();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setProjectId(m.getProjectId());
        e.setSourceLang(m.getSourceLang()); e.setTargetLang(m.getTargetLang());
        e.setSourceText(m.getSourceText()); e.setTargetText(m.getTargetText());
        e.setEmbedding(m.getEmbedding()); e.setUsageCount(m.getUsageCount());
        e.setSourceEngine(m.getSourceEngine()); e.setTranslationMode(m.getTranslationMode());
        e.setTenantId(m.getTenantId()); e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime()); e.setDeleted(m.getDeleted()); return e;
    }
    public static List<com.yumu.noveltranslator.domain.model.TranslationMemory> toModelListMemories(List<TranslationMemory> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toModelMemory).collect(Collectors.toList());
    }
    public static List<TranslationMemory> toEntityListMemories(List<com.yumu.noveltranslator.domain.model.TranslationMemory> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toEntityMemory).collect(Collectors.toList());
    }

    public static com.yumu.noveltranslator.domain.model.TranslationCache toModelCache(TranslationCache e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.TranslationCache();
        m.setId(e.getId()); m.setCacheKey(e.getCacheKey()); m.setSourceText(e.getSourceText());
        m.setTargetText(e.getTargetText()); m.setSourceLang(e.getSourceLang());
        m.setTargetLang(e.getTargetLang()); m.setEngine(e.getEngine()); m.setMode(e.getMode());
        m.setVersion(e.getVersion()); m.setExpireTime(e.getExpireTime());
        m.setCreateTime(e.getCreateTime()); return m;
    }
    public static TranslationCache toEntityCache(com.yumu.noveltranslator.domain.model.TranslationCache m) {
        if (m == null) return null;
        var e = new TranslationCache();
        e.setId(m.getId()); e.setCacheKey(m.getCacheKey()); e.setSourceText(m.getSourceText());
        e.setTargetText(m.getTargetText()); e.setSourceLang(m.getSourceLang());
        e.setTargetLang(m.getTargetLang()); e.setEngine(m.getEngine()); e.setMode(m.getMode());
        e.setVersion(m.getVersion()); e.setExpireTime(m.getExpireTime());
        e.setCreateTime(m.getCreateTime()); return e;
    }
    public static List<com.yumu.noveltranslator.domain.model.TranslationCache> toModelListCaches(List<TranslationCache> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toModelCache).collect(Collectors.toList());
    }
    public static List<TranslationCache> toEntityListCaches(List<com.yumu.noveltranslator.domain.model.TranslationCache> l) {
        if (l == null) return null; return l.stream().map(TranslationConverter::toEntityCache).collect(Collectors.toList());
    }
}
