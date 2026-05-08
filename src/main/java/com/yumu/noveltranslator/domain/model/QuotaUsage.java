package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QuotaUsage {
    private Long id;
    private Long userId;
    private LocalDate usageDate;
    private Long charactersUsed;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
