package com.yumu.noveltranslator.adapter.in.rest.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.dto.entity.ApiKeyResponse;
import com.yumu.noveltranslator.dto.entity.CreateApiKeyRequest;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ApiKey;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService;
import com.yumu.noveltranslator.util.OwnershipVerifier;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web API Key 管理接口
 * 路径前缀: /user/api-keys
 */
@RestController
@RequestMapping("/user/api-keys")
public class WebApiKeyController {

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyCacheService apiKeyCacheService;
    private final OwnershipVerifier ownershipVerifier;

    public WebApiKeyController(ApiKeyMapper apiKeyMapper, ApiKeyCacheService apiKeyCacheService,
                                OwnershipVerifier ownershipVerifier) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyCacheService = apiKeyCacheService;
        this.ownershipVerifier = ownershipVerifier;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String API_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成 API Key
     * POST /user/api-keys
     */
    @PostMapping
    public Result<Map<String, Object>> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        String name = request.getName();

        String key = generateApiKey();
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setApiKey(key);
        apiKey.setName(name);
        apiKey.setActive(true);
        apiKey.setTotalUsage(0L);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKeyMapper.insert(apiKey);

        return Result.ok(Map.of(
            "id", apiKey.getId(),
            "apiKey", key,
            "name", name,
            "isActive", true,
            "createdAt", apiKey.getCreatedAt()
        ));
    }

    /**
     * 获取 API Key 列表
     * GET /user/api-keys
     */
    @GetMapping
    public Result<PageResponse<ApiKeyResponse>> getApiKeys(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtil.getRequiredUserId();
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getUserId, userId).orderByDesc(ApiKey::getCreatedAt);
        Page<ApiKey> pageParam = new Page<>(page, pageSize);
        Page<ApiKey> resultPage = apiKeyMapper.selectPage(pageParam, wrapper);
        List<ApiKeyResponse> responses = resultPage.getRecords().stream().map(k -> {
            ApiKeyResponse r = new ApiKeyResponse();
            r.setId(k.getId());
            r.setName(k.getName());
            r.setApiKey(SecurityUtil.maskApiKey(k.getApiKey()));
            r.setActive(k.getActive());
            r.setLastUsedAt(k.getLastUsedAt());
            r.setTotalUsage(k.getTotalUsage());
            r.setCreatedAt(k.getCreatedAt());
            return r;
        }).collect(Collectors.toList());
        return Result.ok(PageResponse.of(page, pageSize, resultPage.getTotal(), responses));
    }

    /**
     * 禁用/删除 API Key
     * DELETE /user/api-keys/{id}
     */
    @DeleteMapping("/{id}")
    public Result deleteApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = ownershipVerifier.verifyAndGet(id, userId, apiKeyMapper::selectById, ApiKey::getUserId)
                .orElse(null);
        if (key == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        apiKeyCacheService.invalidate(key.getApiKey());
        apiKeyMapper.deleteById(id);
        return Result.ok(null);
    }

    /**
     * 重置 API Key
     * POST /user/api-keys/{id}/reset
     */
    @PostMapping("/{id}/reset")
    public Result<Map<String, Object>> resetApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = ownershipVerifier.verifyAndGet(id, userId, apiKeyMapper::selectById, ApiKey::getUserId)
                .orElse(null);
        if (key == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        // 使旧 key 缓存失效
        apiKeyCacheService.invalidate(key.getApiKey());
        String newKey = generateApiKey();
        key.setApiKey(newKey);
        key.setTotalUsage(0L);
        apiKeyMapper.updateById(key);
        return Result.ok(Map.of("id", key.getId(), "apiKey", newKey));
    }

    /**
     * 查看 API Key 真实值（不掩码）
     * GET /user/api-keys/{id}/reveal
     */
    @GetMapping("/{id}/reveal")
    public Result<Map<String, Object>> revealApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = ownershipVerifier.verifyAndGet(id, userId, apiKeyMapper::selectById, ApiKey::getUserId)
                .orElse(null);
        if (key == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        return Result.ok(Map.of("id", key.getId(), "apiKey", key.getApiKey()));
    }

    private String generateApiKey() {
        StringBuilder sb = new StringBuilder("nt_sk_");
        for (int i = 0; i < 32; i++) {
            sb.append(API_KEY_CHARS.charAt(SECURE_RANDOM.nextInt(API_KEY_CHARS.length())));
        }
        return sb.toString();
    }

}
