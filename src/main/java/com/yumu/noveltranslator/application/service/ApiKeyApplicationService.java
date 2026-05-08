package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.in.ApiKeyPort;
import com.yumu.noveltranslator.port.out.ApiKeyCachePort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyApplicationService implements ApiKeyPort {

    private final UserRepositoryPort userRepositoryPort;
    private final ApiKeyCachePort apiKeyCachePort;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String API_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    @Override
    public ApiKey createApiKey(Long userId, String name) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setApiKey(generateApiKey());
        apiKey.setName(name);
        apiKey.setActive(true);
        apiKey.setTotalUsage(0L);
        apiKey.setCreatedAt(LocalDateTime.now());

        userRepositoryPort.saveApiKey(apiKey);
        return apiKey;
    }

    @Override
    public PageResponse<ApiKey> listApiKeys(Long userId, int page, int pageSize) {
        List<ApiKey> allKeys = userRepositoryPort.findApiKeysByUserId(userId);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allKeys.size());
        List<ApiKey> pagedKeys = allKeys.subList(Math.max(0, start), Math.max(0, end));
        return PageResponse.of(page, pageSize, (long) allKeys.size(), pagedKeys);
    }

    @Override
    public boolean deleteApiKey(Long id, Long userId) {
        return userRepositoryPort.findApiKeyById(id)
                .filter(key -> key.getUserId().equals(userId))
                .map(key -> {
                    apiKeyCachePort.invalidate(key.getApiKey());
                    userRepositoryPort.deleteApiKey(id);
                    return true;
                }).orElse(false);
    }

    @Override
    public ApiKey resetApiKey(Long id, Long userId) {
        return userRepositoryPort.findApiKeyById(id)
                .filter(key -> key.getUserId().equals(userId))
                .map(key -> {
                    apiKeyCachePort.invalidate(key.getApiKey());
                    key.setApiKey(generateApiKey());
                    key.setTotalUsage(0L);
                    userRepositoryPort.updateApiKey(key);
                    return key;
                }).orElse(null);
    }

    @Override
    public ApiKey revealApiKey(Long id, Long userId) {
        return userRepositoryPort.findApiKeyById(id)
                .filter(key -> key.getUserId().equals(userId))
                .orElse(null);
    }

    private String generateApiKey() {
        StringBuilder sb = new StringBuilder("nt_sk_");
        for (int i = 0; i < 32; i++) {
            sb.append(API_KEY_CHARS.charAt(SECURE_RANDOM.nextInt(API_KEY_CHARS.length())));
        }
        return sb.toString();
    }
}
