package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.dto.entity.ApiKeyResponse;
import com.yumu.noveltranslator.dto.entity.CreateApiKeyRequest;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.port.in.ApiKeyPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web API Key 管理接口
 * 路径前缀: /user/api-keys
 */
@RestController
@RequestMapping("/user/api-keys")
@RequiredArgsConstructor
public class WebApiKeyController {

    private final ApiKeyPort apiKeyPort;

    /**
     * 生成 API Key
     * POST /user/api-keys
     */
    @PostMapping
    public Result<Map<String, Object>> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey apiKey = apiKeyPort.createApiKey(userId, request.getName());

        return Result.ok(Map.of(
            "id", apiKey.getId(),
            "apiKey", apiKey.getApiKey(),
            "name", apiKey.getName(),
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
        PageResponse<ApiKey> result = apiKeyPort.listApiKeys(userId, page, pageSize);
        List<ApiKeyResponse> responses = result.getList().stream().map(k -> {
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
        return Result.ok(PageResponse.of(page, pageSize, result.getTotal(), responses));
    }

    /**
     * 禁用/删除 API Key
     * DELETE /user/api-keys/{id}
     */
    @DeleteMapping("/{id}")
    public Result deleteApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        boolean success = apiKeyPort.deleteApiKey(id, userId);
        if (!success) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        return Result.ok(null);
    }

    /**
     * 重置 API Key
     * POST /user/api-keys/{id}/reset
     */
    @PostMapping("/{id}/reset")
    public Result<Map<String, Object>> resetApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = apiKeyPort.resetApiKey(id, userId);
        if (key == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        return Result.ok(Map.of("id", key.getId(), "apiKey", key.getApiKey()));
    }

    /**
     * 查看 API Key 真实值（不掩码）
     * GET /user/api-keys/{id}/reveal
     */
    @GetMapping("/{id}/reveal")
    public Result<Map<String, Object>> revealApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = apiKeyPort.revealApiKey(id, userId);
        if (key == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "API Key 不存在");
        }
        return Result.ok(Map.of("id", key.getId(), "apiKey", key.getApiKey()));
    }
}
