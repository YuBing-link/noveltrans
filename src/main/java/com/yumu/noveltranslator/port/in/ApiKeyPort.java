package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.port.dto.common.PageResponse;

import java.util.List;

/**
 * API Key management use-case port.
 */
public interface ApiKeyPort {

    ApiKey createApiKey(Long userId, String name);

    PageResponse<ApiKey> listApiKeys(Long userId, int page, int pageSize);

    boolean deleteApiKey(Long id, Long userId);

    ApiKey resetApiKey(Long id, Long userId);

    ApiKey revealApiKey(Long id, Long userId);
}
