package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiKey {
    private Long id;
    private Long userId;
    private String apiKey;
    private String name;
    private Boolean active;
    private LocalDateTime lastUsedAt;
    private Long totalUsage;
    private Long tenantId;
    private LocalDateTime createdAt;
}
