package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import com.yumu.noveltranslator.port.in.CacheAdminPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheAdminPortAdapter implements CacheAdminPort {

    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;

    @Override
    public void clearAllTranslationCache() {
        log.info("请求清空所有翻译缓存");
        cacheService.clearAllCache();
        ragTranslationService.clearAllTranslationMemory();
    }
}
