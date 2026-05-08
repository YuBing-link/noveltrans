package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiGlossary {
    private Long id;
    private Long projectId;
    private String sourceWord;
    private String targetWord;
    private String context;
    private String entityType;
    private Long chapterId;
    private Double confidence;
    private String status;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
