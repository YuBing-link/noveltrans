package com.yumu.noveltranslator.controller.admin;

import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationCacheService;
import com.yumu.noveltranslator.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存管理接口（调试用，上线后应移除或加鉴权）
 */
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheAdminController {

    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;

    /**
     * 清空所有翻译缓存（L1 + L2 + L3）
     */
    @PostMapping("/clear")
    public Result<String> clearAllCache() {
        log.info("请求清空所有翻译缓存");
        cacheService.clearAllCache();
        ragTranslationService.clearAllTranslationMemory();
        return Result.ok("缓存已清空");
    }
}
