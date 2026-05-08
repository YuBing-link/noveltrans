package com.yumu.noveltranslator.port.out;

/**
 * Redis 向量搜索结果，由 VectorStorePort adapter 层组装。
 */
public record VectorSearchResult(
        Long memoryId,
        String sourceText,
        String targetText,
        double similarity
) {}
