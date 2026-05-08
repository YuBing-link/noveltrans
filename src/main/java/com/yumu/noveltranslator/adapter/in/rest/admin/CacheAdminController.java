package com.yumu.noveltranslator.adapter.in.rest.admin;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.in.CacheAdminPort;
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

    private final CacheAdminPort cacheAdminPort;

    /**
     * 清空所有翻译缓存（L1 + L2 + L3）
     */
    @PostMapping("/clear")
    public Result<String> clearAllCache() {
        cacheAdminPort.clearAllTranslationCache();
        return Result.ok("缓存已清空");
    }
}
