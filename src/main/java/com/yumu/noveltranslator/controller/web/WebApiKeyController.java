package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.ApiKeyResponse;
import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.entity.ApiKey;
import com.yumu.noveltranslator.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WebApiKeyController {

    private final ApiKeyMapper apiKeyMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String API_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成 API Key
     * POST /user/api-keys
     */
    @PostMapping
    public Result<Map<String, Object>> createApiKey(@RequestBody(required = false) Map<String, String> request) {
        Long userId = SecurityUtil.getRequiredUserId();
        String name = request != null && request.containsKey("name") ? request.get("name") : "Default";

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
    public Result<List<ApiKeyResponse>> getApiKeys() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<ApiKey> keys = apiKeyMapper.findByUserId(userId);
        List<ApiKeyResponse> responses = keys.stream().map(k -> {
            ApiKeyResponse r = new ApiKeyResponse();
            r.setId(k.getId());
            r.setName(k.getName());
            r.setApiKey(maskApiKey(k.getApiKey()));
            r.setActive(k.getActive());
            r.setLastUsedAt(k.getLastUsedAt());
            r.setTotalUsage(k.getTotalUsage());
            r.setCreatedAt(k.getCreatedAt());
            return r;
        }).collect(Collectors.toList());
        return Result.ok(responses);
    }

    /**
     * 禁用/删除 API Key
     * DELETE /user/api-keys/{id}
     */
    @DeleteMapping("/{id}")
    public Result deleteApiKey(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null || !key.getUserId().equals(userId)) {
            return Result.error("API Key 不存在");
        }
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
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null || !key.getUserId().equals(userId)) {
            return Result.error("API Key 不存在");
        }
        String newKey = generateApiKey();
        key.setApiKey(newKey);
        key.setTotalUsage(0L);
        apiKeyMapper.updateById(key);
        return Result.ok(Map.of("id", key.getId(), "apiKey", newKey));
    }

    private String generateApiKey() {
        StringBuilder sb = new StringBuilder("nt_sk_");
        for (int i = 0; i < 32; i++) {
            sb.append(API_KEY_CHARS.charAt(SECURE_RANDOM.nextInt(API_KEY_CHARS.length())));
        }
        return sb.toString();
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 16) return "***";
        return key.substring(0, 10) + "****" + key.substring(key.length() - 4);
    }
}
