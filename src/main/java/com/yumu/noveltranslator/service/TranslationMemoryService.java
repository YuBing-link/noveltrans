package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TranslationMemory;
import com.yumu.noveltranslator.mapper.TranslationMemoryMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 翻译记忆服务
 * 管理翻译记忆的存储和 MySQL 层查询
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationMemoryService extends ServiceImpl<TranslationMemoryMapper, TranslationMemory> {

    private final TranslationMemoryMapper translationMemoryMapper;
    private final EmbeddingService embeddingService;

    /**
     * 存储翻译记忆（写入 MySQL）
     */
    public void storeTranslation(String sourceText, String targetText, String sourceLang, String targetLang,
                                  Long userId, Long projectId, String engine) {
        if (sourceText == null || sourceText.isBlank() || targetText == null || targetText.isBlank()) {
            return;
        }

        TranslationMemory memory = new TranslationMemory();
        memory.setUserId(userId);
        memory.setProjectId(projectId);
        memory.setSourceLang(sourceLang);
        memory.setTargetLang(targetLang);
        memory.setSourceText(sourceText);
        memory.setTargetText(targetText);
        memory.setSourceEngine(engine);
        memory.setUsageCount(0);

        // 生成向量
        float[] embedding = embeddingService.embed(sourceText);
        if (embedding.length > 0) {
            memory.setEmbedding(toFloatList(embedding));
        }

        save(memory);
        log.debug("存储翻译记忆: id={}, sourceLen={}", memory.getId(), sourceText.length());
    }

    /**
     * 增加使用计数
     */
    public void incrementUsage(Long memoryId) {
        TranslationMemory memory = getById(memoryId);
        if (memory != null) {
            memory.setUsageCount(memory.getUsageCount() + 1);
            updateById(memory);
        }
    }

    /**
     * 按用户和语言对查询翻译记忆（MySQL 层，精确匹配）
     */
    public List<TranslationMemory> searchByUserAndLang(Long userId, String sourceLang, String targetLang, int limit) {
        return translationMemoryMapper.selectTopByUserAndLang(userId, sourceLang, targetLang, limit);
    }

    /**
     * 按项目查询翻译记忆
     */
    public List<TranslationMemory> searchByProject(Long projectId) {
        return translationMemoryMapper.selectByProjectId(projectId);
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }
}
