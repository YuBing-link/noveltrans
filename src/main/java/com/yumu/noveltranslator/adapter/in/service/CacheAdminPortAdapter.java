package com.yumu.noveltranslator.adapter.in.service;

import com.yumu.noveltranslator.port.in.CacheAdminPort;
import com.yumu.noveltranslator.port.out.TranslationCacheAdminPort;
import com.yumu.noveltranslator.port.in.RagTranslationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheAdminPortAdapter implements CacheAdminPort {

    private final TranslationCacheAdminPort cacheAdminPort;
    private final RagTranslationPort ragTranslationPort;

    @Override
    public void clearAllTranslationCache() {
        log.info("请求清空所有翻译缓存");
        cacheAdminPort.clearAllCache();
        ragTranslationPort.clearAllTranslationMemory();
    }
}
