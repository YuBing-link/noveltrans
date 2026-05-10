package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.AiGlossary;
import com.yumu.noveltranslator.domain.model.ChapterEntityMap;
import com.yumu.noveltranslator.domain.model.Glossary;
import com.yumu.noveltranslator.domain.model.TranslationMemory;
import com.yumu.noveltranslator.port.dto.common.PageResult;

import java.util.List;
import java.util.Optional;

public interface GlossaryRepositoryPort {

    // === Glossary ===
    void saveGlossary(Glossary glossary);
    void updateGlossary(Glossary glossary);
    boolean deleteGlossary(Glossary glossary);
    Optional<Glossary> findGlossaryById(Long id);
    List<Glossary> findGlossaryByUserId(Long userId);
    List<Glossary> findGlossaryByUserIdIncludeDeleted(Long userId);
    List<Glossary> findActiveGlossaryByUserId(Long userId);
    PageResult<Glossary> findGlossaryPaged(Long userId, String search, int page, int pageSize);
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
