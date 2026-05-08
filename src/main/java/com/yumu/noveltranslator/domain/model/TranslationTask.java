package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TranslationTask {
    private Long id;
    private String taskId;
    private Long userId;
    private String type;
    private Long documentId;
    private String sourceLang;
    private String targetLang;
    private String mode;
    private String engine;
    private String status;
    private Integer progress;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completedTime;
    private String errorMessage;
    private Integer deleted;
}
