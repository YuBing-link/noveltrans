package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 翻译记忆服务
 * 管理翻译记忆的存储和 MySQL 层查询
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TranslationMemoryService {

    private final GlossaryRepositoryPort glossaryPort;
    private final EmbeddingService embeddingService;

    /**
     * 存储翻译记忆（写入 MySQL）
     */
    public void storeTranslation(String sourceText, String targetText, String sourceLang, String targetLang,
                                  Long userId, Long projectId, String engine, String translationMode) {
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
        memory.setTranslationMode(translationMode);
        memory.setUsageCount(0);

        // 生成向量
        float[] embedding = embeddingService.embed(sourceText);
        if (embedding.length > 0) {
            memory.setEmbedding(toFloatList(embedding));
        }

        glossaryPort.saveTranslationMemory(memory);
        log.debug("存储翻译记忆: id={}, sourceLen={}", memory.getId(), sourceText.length());
    }

    /**
     * 增加使用计数
     */
    public void incrementUsage(Long memoryId) {
        glossaryPort.incrementMemoryUsage(memoryId);
    }

    /**
     * 按用户和语言对查询翻译记忆（MySQL 层，精确匹配）
     */
    public List<TranslationMemory> searchByUserAndLang(Long userId, String sourceLang, String targetLang, int limit) {
        return glossaryPort.findTopMemoryByUserAndLang(userId, sourceLang, targetLang, limit);
    }

    /**
     * 按项目查询翻译记忆
     */
    public List<TranslationMemory> searchByProject(Long projectId) {
        return glossaryPort.findMemoryByProjectId(projectId);
    }

    public void deleteAllTranslationMemory() {
        glossaryPort.deleteAllTranslationMemory();
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }
}
