package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabProject {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private Long documentId;
    private String sourceLang;
    private String targetLang;
    private String status;
    private Integer progress;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
