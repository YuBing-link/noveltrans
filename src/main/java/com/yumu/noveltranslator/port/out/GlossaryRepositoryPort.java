package com.yumu.noveltranslator.port.out;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.adapter.out.persistence.entity.AiGlossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ChapterEntityMap;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;

import java.util.List;
import java.util.Optional;

public interface GlossaryRepositoryPort {

    // === Glossary ===
    void saveGlossary(Glossary glossary);
    void updateGlossary(Glossary glossary);
    Optional<Glossary> findGlossaryById(Long id);
    List<Glossary> findGlossaryByUserId(Long userId);
    List<Glossary> findActiveGlossaryByUserId(Long userId);
    IPage<Glossary> findGlossaryPaged(Long userId, String search, int page, int pageSize);
    int countAllGlossaries();

    // === AiGlossary ===
    List<AiGlossary> findAiGlossaryByProjectId(Long projectId);
    List<AiGlossary> findAiGlossaryByProjectIdAndStatus(Long projectId, String status);
    List<AiGlossary> findAiGlossaryByChapterId(Long chapterId);
    void saveAiGlossary(AiGlossary aiGlossary);
    void updateAiGlossary(AiGlossary aiGlossary);
    void deleteAiGlossary(Long id);
    Optional<AiGlossary> findAiGlossaryById(Long id);
    AiGlossary findAiGlossaryByProjectIdAndSourceWord(Long projectId, String sourceWord);
    void upsertAiGlossary(Long projectId, String sourceWord, String targetWord, String context, String entityType, Long chapterId);

    // === ChapterEntityMap ===
    List<ChapterEntityMap> findEntityMapsByChapterId(Long chapterId);
    List<ChapterEntityMap> findEntityMapsByProjectId(Long projectId);
    void saveEntityMap(ChapterEntityMap entityMap);
    void updateEntityMap(ChapterEntityMap entityMap);

    // === TranslationMemory ===
    List<TranslationMemory> findTopMemoryByUserAndLang(Long userId, String sourceLang, String targetLang, int limit);
    List<TranslationMemory> findMemoryByProjectId(Long projectId);
    void saveTranslationMemory(TranslationMemory memory);
    void updateTranslationMemory(TranslationMemory memory);
    Optional<TranslationMemory> findMemoryById(Long id);
    void incrementMemoryUsage(Long id);
    void deleteAllTranslationMemory();
}
