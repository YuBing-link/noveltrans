package com.yumu.noveltranslator.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void translationCacheGettersSetters() {
        TranslationCache cache = new TranslationCache();
        cache.setId(1L);
        cache.setCacheKey("abc123");
        cache.setSourceText("Hello");
        cache.setTargetText("你好");
        cache.setSourceLang("en");
        cache.setTargetLang("zh");
        cache.setEngine("google");
        LocalDateTime now = LocalDateTime.now();
        cache.setExpireTime(now.plusHours(24));
        cache.setCreateTime(now);

        assertEquals(1L, cache.getId());
        assertEquals("abc123", cache.getCacheKey());
        assertEquals("Hello", cache.getSourceText());
        assertEquals("你好", cache.getTargetText());
        assertEquals("en", cache.getSourceLang());
        assertEquals("zh", cache.getTargetLang());
        assertEquals("google", cache.getEngine());
    }

    @Test
    void translationTaskGettersSetters() {
        TranslationTask task = new TranslationTask();
        task.setId(1L);
        task.setUserId(10L);
        task.setTaskId("task-001");
        task.setSourceLang("en");
        task.setTargetLang("zh");
        task.setStatus("completed");
        task.setProgress(100);
        task.setType("document");
        task.setEngine("google");
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);

        assertEquals(1L, task.getId());
        assertEquals("task-001", task.getTaskId());
        assertEquals(100, task.getProgress());
    }

    @Test
    void glossaryGettersSetters() {
        Glossary glossary = new Glossary();
        glossary.setId(1L);
        glossary.setUserId(10L);
        glossary.setSourceWord("hello");
        glossary.setTargetWord("你好");
        glossary.setRemark("greeting");
        glossary.setDeleted(0);

        assertEquals(1L, glossary.getId());
        assertEquals("hello", glossary.getSourceWord());
        assertEquals("你好", glossary.getTargetWord());
    }

    @Test
    void translationHistoryGettersSetters() {
        TranslationHistory history = new TranslationHistory();
        history.setId(1L);
        history.setUserId(10L);
        history.setSourceText("Hello World");
        history.setTargetText("你好世界");
        history.setSourceLang("en");
        history.setTargetLang("zh");
        history.setEngine("google");

        assertEquals(1L, history.getId());
        assertEquals("Hello World", history.getSourceText());
        assertEquals("google", history.getEngine());
    }

    @Test
    void documentGettersSetters() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setUserId(10L);
        doc.setName("test.docx");
        doc.setFileType("docx");
        doc.setFileSize(1024L);
        doc.setSourceLang("en");
        doc.setTargetLang("zh");
        doc.setStatus("processing");

        assertEquals(1L, doc.getId());
        assertEquals("test.docx", doc.getName());
        assertEquals(1024L, doc.getFileSize());
    }
}
