package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.adapter.out.persistence.converter.GlossaryConverter;
import com.yumu.noveltranslator.adapter.out.persistence.entity.AiGlossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ChapterEntityMap;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.AiGlossaryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ChapterEntityMapMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.GlossaryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationMemoryMapper;
import com.yumu.noveltranslator.port.dto.common.PageResult;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GlossaryRepositoryAdapter implements GlossaryRepositoryPort {

    private final GlossaryMapper glossaryMapper;
    private final AiGlossaryMapper aiGlossaryMapper;
    private final ChapterEntityMapMapper chapterEntityMapMapper;
    private final TranslationMemoryMapper translationMemoryMapper;

    @Override
    public void saveGlossary(com.yumu.noveltranslator.domain.model.Glossary glossary) {
        glossaryMapper.insert(GlossaryConverter.glossaryToEntity(glossary));
    }

    @Override
    public void updateGlossary(com.yumu.noveltranslator.domain.model.Glossary glossary) {
        glossaryMapper.updateById(GlossaryConverter.glossaryToEntity(glossary));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.Glossary> findGlossaryById(Long id) {
        return Optional.ofNullable(GlossaryConverter.glossaryToModel(glossaryMapper.selectById(id)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.Glossary> findGlossaryByUserId(Long userId) {
        return glossaryMapper.selectList(new LambdaQueryWrapper<Glossary>()
                .eq(Glossary::getUserId, userId)
                .orderByDesc(Glossary::getCreateTime)).stream()
                .map(GlossaryConverter::glossaryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.Glossary> findActiveGlossaryByUserId(Long userId) {
        return glossaryMapper.selectList(new LambdaQueryWrapper<Glossary>()
                .eq(Glossary::getUserId, userId)
                .eq(Glossary::getDeleted, 0)
                .orderByDesc(Glossary::getCreateTime)).stream()
                .map(GlossaryConverter::glossaryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<com.yumu.noveltranslator.domain.model.Glossary> findGlossaryPaged(Long userId, String search, int page, int pageSize) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId);
        if (search != null && !search.isEmpty()) {
            wrapper.and(w -> w.like(Glossary::getSourceWord, search)
                    .or()
                    .like(Glossary::getTargetWord, search));
        }
        wrapper.orderByDesc(Glossary::getCreateTime);
        IPage<Glossary> entityPage = glossaryMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<com.yumu.noveltranslator.domain.model.Glossary> models = entityPage.getRecords().stream()
                .map(GlossaryConverter::glossaryToModel)
                .collect(Collectors.toList());
        return new PageResult<>(models, entityPage.getTotal(), entityPage.getCurrent(), entityPage.getSize());
    }

    @Override
    public int countAllGlossaries() {
        return Math.toIntExact(glossaryMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.AiGlossary> findAiGlossaryByProjectId(Long projectId) {
        return aiGlossaryMapper.selectByProjectId(projectId).stream()
                .map(GlossaryConverter::aiGlossaryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.AiGlossary> findAiGlossaryByProjectIdAndStatus(Long projectId, String status) {
        return aiGlossaryMapper.selectByProjectIdAndStatus(projectId, status).stream()
                .map(GlossaryConverter::aiGlossaryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.AiGlossary> findAiGlossaryByChapterId(Long chapterId) {
        return aiGlossaryMapper.selectByChapterId(chapterId).stream()
                .map(GlossaryConverter::aiGlossaryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public void saveAiGlossary(com.yumu.noveltranslator.domain.model.AiGlossary aiGlossary) {
        aiGlossaryMapper.insert(GlossaryConverter.aiGlossaryToEntity(aiGlossary));
    }

    @Override
    public void updateAiGlossary(com.yumu.noveltranslator.domain.model.AiGlossary aiGlossary) {
        aiGlossaryMapper.updateById(GlossaryConverter.aiGlossaryToEntity(aiGlossary));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.AiGlossary> findAiGlossaryById(Long id) {
        return Optional.ofNullable(GlossaryConverter.aiGlossaryToModel(aiGlossaryMapper.selectById(id)));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.AiGlossary findAiGlossaryByProjectIdAndSourceWord(Long projectId, String sourceWord) {
        LambdaQueryWrapper<AiGlossary> query = new LambdaQueryWrapper<>();
        query.eq(AiGlossary::getProjectId, projectId)
                .eq(AiGlossary::getSourceWord, sourceWord);
        return GlossaryConverter.aiGlossaryToModel(aiGlossaryMapper.selectOne(query));
    }

    @Override
    public void upsertAiGlossary(Long projectId, String sourceWord, String targetWord, String context, String entityType, Long chapterId) {
        com.yumu.noveltranslator.domain.model.AiGlossary existing = findAiGlossaryByProjectIdAndSourceWord(projectId, sourceWord);
        if (existing != null) {
            LambdaUpdateWrapper<AiGlossary> update = new LambdaUpdateWrapper<>();
            update.eq(AiGlossary::getId, existing.getId())
                    .set(AiGlossary::getTargetWord, targetWord);
            if (context != null) {
                update.set(AiGlossary::getContext, context);
            }
            if (chapterId != null) {
                update.set(AiGlossary::getChapterId, chapterId);
            }
            aiGlossaryMapper.update(null, update);
        } else {
            com.yumu.noveltranslator.domain.model.AiGlossary term = new com.yumu.noveltranslator.domain.model.AiGlossary();
            term.setProjectId(projectId);
            term.setSourceWord(sourceWord);
            term.setTargetWord(targetWord);
            term.setContext(context);
            term.setEntityType(entityType);
            term.setChapterId(chapterId);
            term.setConfidence(0.8);
            term.setStatus("pending");
            aiGlossaryMapper.insert(GlossaryConverter.aiGlossaryToEntity(term));
        }
    }

    @Override
    public void deleteAiGlossary(Long id) {
        aiGlossaryMapper.deleteById(id);
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.ChapterEntityMap> findEntityMapsByChapterId(Long chapterId) {
        return chapterEntityMapMapper.selectByChapterId(chapterId).stream()
                .map(GlossaryConverter::chapterMapToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.ChapterEntityMap> findEntityMapsByProjectId(Long projectId) {
        return chapterEntityMapMapper.selectByProjectId(projectId).stream()
                .map(GlossaryConverter::chapterMapToModel)
                .collect(Collectors.toList());
    }

    @Override
    public void saveEntityMap(com.yumu.noveltranslator.domain.model.ChapterEntityMap entityMap) {
        chapterEntityMapMapper.insert(GlossaryConverter.chapterMapToEntity(entityMap));
    }

    @Override
    public void updateEntityMap(com.yumu.noveltranslator.domain.model.ChapterEntityMap entityMap) {
        chapterEntityMapMapper.updateById(GlossaryConverter.chapterMapToEntity(entityMap));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationMemory> findTopMemoryByUserAndLang(Long userId, String sourceLang, String targetLang, int limit) {
        return translationMemoryMapper.selectTopByUserAndLang(userId, sourceLang, targetLang, limit).stream()
                .map(GlossaryConverter::translationMemoryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationMemory> findMemoryByProjectId(Long projectId) {
        return translationMemoryMapper.selectByProjectId(projectId).stream()
                .map(GlossaryConverter::translationMemoryToModel)
                .collect(Collectors.toList());
    }

    @Override
    public void saveTranslationMemory(com.yumu.noveltranslator.domain.model.TranslationMemory memory) {
        translationMemoryMapper.insert(GlossaryConverter.translationMemoryToEntity(memory));
    }

    @Override
    public void updateTranslationMemory(com.yumu.noveltranslator.domain.model.TranslationMemory memory) {
        translationMemoryMapper.updateById(GlossaryConverter.translationMemoryToEntity(memory));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TranslationMemory> findMemoryById(Long id) {
        return Optional.ofNullable(GlossaryConverter.translationMemoryToModel(translationMemoryMapper.selectById(id)));
    }

    @Override
    public void incrementMemoryUsage(Long id) {
        TranslationMemory memory = translationMemoryMapper.selectById(id);
        if (memory != null) {
            memory.setUsageCount(memory.getUsageCount() + 1);
            translationMemoryMapper.updateById(memory);
        }
    }

    @Override
    public void deleteAllTranslationMemory() {
        translationMemoryMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
    }
}
