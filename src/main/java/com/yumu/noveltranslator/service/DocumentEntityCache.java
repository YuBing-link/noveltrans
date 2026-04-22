package com.yumu.noveltranslator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yumu.noveltranslator.entity.ChapterEntityMap;
import com.yumu.noveltranslator.mapper.ChapterEntityMapMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文档级实体翻译缓存
 * 采用 L1 (Caffeine 内存) + L3 (数据库) 两级缓存架构
 * 同一文档的不同翻译段落共享已确认的实体翻译，避免重复调用 LLM
 * 服务重启后可从数据库恢复实体映射
 */
@Component
@Slf4j
public class DocumentEntityCache {

    /**
     * L1 缓存: key: userId:documentId, value: 原文实体 → 译文实体的映射
     * TTL: 30 分钟
     */
    private final Cache<String, Map<String, String>> entityCache;

    @Autowired
    private ChapterEntityMapMapper chapterEntityMapMapper;

    public DocumentEntityCache() {
        this.entityCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    /**
     * 获取文档的实体映射
     * 先查 L1 缓存，未命中则查询数据库并回填缓存
     */
    public Map<String, String> getEntityMap(Long userId, String documentId) {
        String key = buildKey(userId, documentId);
        Map<String, String> map = entityCache.getIfPresent(key);
        if (map != null) {
            return Collections.unmodifiableMap(map);
        }

        // L1 未命中，查询数据库 (L3)
        try {
            Long chapterId = parseChapterId(documentId);
            if (chapterId != null) {
                List<ChapterEntityMap> dbMappings = chapterEntityMapMapper.selectByChapterId(chapterId);
                if (!dbMappings.isEmpty()) {
                    Map<String, String> dbMap = new ConcurrentHashMap<>();
                    for (ChapterEntityMap mapping : dbMappings) {
                        dbMap.put(mapping.getSourceEntity(), mapping.getTargetEntity());
                    }
                    entityCache.put(key, dbMap);
                    log.debug("实体映射从数据库加载: chapterId={}, {} 条", chapterId, dbMap.size());
                    return Collections.unmodifiableMap(dbMap);
                }
            }
        } catch (Exception e) {
            log.warn("从数据库加载实体映射失败: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    /**
     * 合并新的实体映射到文档缓存
     * 同时写入数据库 (L3) 和 Caffeine 缓存 (L1)
     */
    public void mergeEntityMap(Long userId, String documentId, Map<String, String> newMappings) {
        if (newMappings == null || newMappings.isEmpty()) {
            return;
        }

        String key = buildKey(userId, documentId);
        entityCache.asMap().compute(key, (k, existing) -> {
            Map<String, String> map = existing != null ? existing : new ConcurrentHashMap<>();
            map.putAll(newMappings);
            return map;
        });

        // 同时持久化到数据库
        try {
            Long chapterId = parseChapterId(documentId);
            if (chapterId != null) {
                for (Map.Entry<String, String> entry : newMappings.entrySet()) {
                    upsertMapping(chapterId, null, entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("持久化实体映射到数据库失败: {}", e.getMessage());
        }

        log.debug("文档实体缓存合并: key={}, 新增 {} 条", key, newMappings.size());
    }

    /**
     * 批量保存实体映射到数据库（带章节和项目上下文）
     *
     * @param chapterId  章节 ID
     * @param projectId  项目 ID（可为 null）
     * @param mappings   原文实体 → 译文实体映射
     */
    public void saveEntityMapping(Long chapterId, Long projectId, Map<String, String> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            upsertMapping(chapterId, projectId, entry.getKey(), entry.getValue());
        }
        log.info("批量保存实体映射到数据库: chapterId={}, projectId={}, {} 条",
                chapterId, projectId, mappings.size());
    }

    /**
     * 清除文档的实体缓存
     */
    public void clear(Long userId, String documentId) {
        String key = buildKey(userId, documentId);
        entityCache.invalidate(key);
    }

    // ==================== 内部方法 ====================

    private String buildKey(Long userId, String documentId) {
        return userId + ":" + documentId;
    }

    /**
     * 尝试从 documentId 解析出 Long 类型的 chapterId
     * 如果 documentId 是纯数字字符串则直接解析，否则返回 null
     */
    private Long parseChapterId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(documentId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Upsert 单条实体映射（INSERT ... ON DUPLICATE KEY UPDATE）
     */
    private void upsertMapping(Long chapterId, Long projectId, String sourceEntity, String targetEntity) {
        ChapterEntityMap existing = new ChapterEntityMap();
        existing.setChapterId(chapterId);
        existing.setProjectId(projectId);
        existing.setSourceEntity(sourceEntity);
        existing.setTargetEntity(targetEntity);

        int inserted = chapterEntityMapMapper.insert(existing);
        if (inserted == 0) {
            // 唯一键冲突，执行更新
            chapterEntityMapMapper.update(existing,
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChapterEntityMap>()
                            .eq(ChapterEntityMap::getChapterId, chapterId)
                            .eq(ChapterEntityMap::getSourceEntity, sourceEntity));
        }
    }
}
