package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChapterEntityMap {
    private Long id;
    private Long chapterId;
    private Long projectId;
    private String sourceEntity;
    private String targetEntity;
    private String entityType;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
