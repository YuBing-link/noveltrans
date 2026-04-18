package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabProjectResponse {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerName;
    private String sourceLang;
    private String targetLang;
    private String status;
    private Integer progress;
    private Integer memberCount;
    private Integer totalChapters;
    private Integer completedChapters;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
