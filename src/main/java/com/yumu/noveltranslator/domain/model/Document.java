package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Document {
    private Long id;
    private Long userId;
    private String name;
    private String path;
    private String sourceLang;
    private String targetLang;
    private String fileType;
    private Long fileSize;
    private String taskId;
    private String status;
    private String mode;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completedTime;
    private String errorMessage;
    private Integer deleted;
}
