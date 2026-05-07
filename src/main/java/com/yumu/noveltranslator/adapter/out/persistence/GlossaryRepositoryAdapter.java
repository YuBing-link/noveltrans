package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.adapter.out.persistence.entity.AiGlossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ChapterEntityMap;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.AiGlossaryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ChapterEntityMapMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.GlossaryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationMemoryMapper;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GlossaryRepositoryAdapter implements GlossaryRepositoryPort {

    private final GlossaryMapper glossaryMapper;
    private final AiGlossaryMapper aiGlossaryMapper;
    private final ChapterEntityMapMapper chapterEntityMapMapper;
    private final TranslationMemoryMapper translationMemoryMapper;

    @Override
    public void saveGlossary(Glossary glossary) {
        glossaryMapper.insert(glossary);
    }

    @Override
    public void updateGlossary(Glossary glossary) {
        glossaryMapper.updateById(glossary);
    }

    @Override
    public Optional<Glossary> findGlossaryById(Long id) {
        return Optional.ofNullable(glossaryMapper.selectById(id));
    }

    @Override
    public List<Glossary> findGlossaryByUserId(Long userId) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId)
               .orderByDesc(Glossary::getCreateTime);
        return glossaryMapper.selectList(wrapper);
    }

    @Override
    public List<Glossary> findActiveGlossaryByUserId(Long userId) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId)
               .eq(Glossary::getDeleted, 0)
               .orderByDesc(Glossary::getCreateTime);
        return glossaryMapper.selectList(wrapper);
    }

    @Override
    public IPage<Glossary> findGlossaryPaged(Long userId, String search, int page, int pageSize) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId);
        if (search != null && !search.isEmpty()) {
            wrapper.and(w -> w.like(Glossary::getSourceWord, search)
                              .or()
                              .like(Glossary::getTargetWord, search));
        }
        wrapper.orderByDesc(Glossary::getCreateTime);
        return glossaryMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public int countAllGlossaries() {
        return Math.toIntExact(glossaryMapper.selectCount(new LambdaQueryWrapper<>()));
    }

    @Override
    public List<AiGlossary> findAiGlossaryByProjectId(Long projectId) {
        return aiGlossaryMapper.selectByProjectId(projectId);
    }

    @Override
    public List<AiGlossary> findAiGlossaryByProjectIdAndStatus(Long projectId, String status) {
        return aiGlossaryMapper.selectByProjectIdAndStatus(projectId, status);
    }

    @Override
    public List<AiGlossary> findAiGlossaryByChapterId(Long chapterId) {
        return aiGlossaryMapper.selectByChapterId(chapterId);
    }

    @Override
    public void saveAiGlossary(AiGlossary aiGlossary) {
        aiGlossaryMapper.insert(aiGlossary);
    }

    @Override
    public void updateAiGlossary(AiGlossary aiGlossary) {
        aiGlossaryMapper.updateById(aiGlossary);
    }

    @Override
    public Optional<AiGlossary> findAiGlossaryById(Long id) {
        return Optional.ofNullable(aiGlossaryMapper.selectById(id));
    }

    @Override
    public AiGlossary findAiGlossaryByProjectIdAndSourceWord(Long projectId, String sourceWord) {
        LambdaQueryWrapper<AiGlossary> query = new LambdaQueryWrapper<>();
        query.eq(AiGlossary::getProjectId, projectId)
             .eq(AiGlossary::getSourceWord, sourceWord);
        return aiGlossaryMapper.selectOne(query);
    }

    @Override
    public void upsertAiGlossary(Long projectId, String sourceWord, String targetWord, String context, String entityType, Long chapterId) {
        AiGlossary existing = findAiGlossaryByProjectIdAndSourceWord(projectId, sourceWord);
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
            AiGlossary term = new AiGlossary();
            term.setProjectId(projectId);
            term.setSourceWord(sourceWord);
            term.setTargetWord(targetWord);
            term.setContext(context);
            term.setEntityType(entityType);
            term.setChapterId(chapterId);
            term.setConfidence(0.8);
            term.setStatus("pending");
            aiGlossaryMapper.insert(term);
        }
    }

    @Override
    public void deleteAiGlossary(Long id) {
        aiGlossaryMapper.deleteById(id);
    }

    @Override
    public List<ChapterEntityMap> findEntityMapsByChapterId(Long chapterId) {
        return chapterEntityMapMapper.selectByChapterId(chapterId);
    }

    @Override
    public List<ChapterEntityMap> findEntityMapsByProjectId(Long projectId) {
        return chapterEntityMapMapper.selectByProjectId(projectId);
    }

    @Override
    public void saveEntityMap(ChapterEntityMap entityMap) {
        chapterEntityMapMapper.insert(entityMap);
    }

    @Override
    public void updateEntityMap(ChapterEntityMap entityMap) {
        chapterEntityMapMapper.updateById(entityMap);
    }

    @Override
    public List<TranslationMemory> findTopMemoryByUserAndLang(Long userId, String sourceLang, String targetLang, int limit) {
        return translationMemoryMapper.selectTopByUserAndLang(userId, sourceLang, targetLang, limit);
    }

    @Override
    public List<TranslationMemory> findMemoryByProjectId(Long projectId) {
        return translationMemoryMapper.selectByProjectId(projectId);
    }

    @Override
    public void saveTranslationMemory(TranslationMemory memory) {
        translationMemoryMapper.insert(memory);
    }

    @Override
    public void updateTranslationMemory(TranslationMemory memory) {
        translationMemoryMapper.updateById(memory);
    }

    @Override
    public Optional<TranslationMemory> findMemoryById(Long id) {
        return Optional.ofNullable(translationMemoryMapper.selectById(id));
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
