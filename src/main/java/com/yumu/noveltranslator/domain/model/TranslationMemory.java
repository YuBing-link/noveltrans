package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TranslationMemory {
    private Long id;
    private Long userId;
    private Long projectId;
    private String sourceLang;
    private String targetLang;
    private String sourceText;
    private String targetText;
    private List<Float> embedding;
    private Integer usageCount;
    private String sourceEngine;
    private String translationMode;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
