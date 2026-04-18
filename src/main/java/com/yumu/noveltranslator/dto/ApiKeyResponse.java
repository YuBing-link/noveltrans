package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * API Key 响应（对外）
 */
@Data
public class ApiKeyResponse {
    private Long id;
    private String name;
    private String apiKey;        // 掩码格式，仅创建时返回完整 key
    private Boolean active;
    private LocalDateTime lastUsedAt;
    private Long totalUsage;
    private LocalDateTime createdAt;
}
