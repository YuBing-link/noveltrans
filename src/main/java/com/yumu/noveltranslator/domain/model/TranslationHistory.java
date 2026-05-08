package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TranslationHistory {
    private Long id;
    private Long userId;
    private String taskId;
    private String type;
    private Long documentId;
    private String sourceLang;
    private String targetLang;
    private String sourceText;
    private String targetText;
    private String engine;
    private Long tenantId;
    private LocalDateTime createTime;
    private Integer deleted;
}
