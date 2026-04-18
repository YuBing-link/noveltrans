package com.yumu.noveltranslator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文档级实体翻译缓存
 * 同一文档的不同翻译段落共享已确认的实体翻译，避免重复调用 LLM
 */
@Component
@Slf4j
public class DocumentEntityCache {

    /**
     * key: userId:documentId
     * value: 原文实体 → 译文实体的映射
     * TTL: 30 分钟
     */
    private final Cache<String, Map<String, String>> entityCache;

    public DocumentEntityCache() {
        this.entityCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    /**
     * 获取文档的实体映射
     */
    public Map<String, String> getEntityMap(Long userId, String documentId) {
        String key = buildKey(userId, documentId);
        Map<String, String> map = entityCache.getIfPresent(key);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    /**
     * 合并新的实体映射到文档缓存
     */
    public void mergeEntityMap(Long userId, String documentId, Map<String, String> newMappings) {
        String key = buildKey(userId, documentId);
        entityCache.asMap().compute(key, (k, existing) -> {
            Map<String, String> map = existing != null ? existing : new ConcurrentHashMap<>();
            map.putAll(newMappings);
            return map;
        });
        log.debug("文档实体缓存合并: key={}, 新增 {} 条", key, newMappings.size());
    }

    /**
     * 清除文档的实体缓存
     */
    public void clear(Long userId, String documentId) {
        String key = buildKey(userId, documentId);
        entityCache.invalidate(key);
    }

    private String buildKey(Long userId, String documentId) {
        return userId + ":" + documentId;
    }
}
