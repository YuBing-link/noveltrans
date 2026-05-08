package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabComment {
    private Long id;
    private Long chapterTaskId;
    private Long userId;
    private String sourceText;
    private String targetText;
    private String content;
    private Long parentId;
    private Boolean resolved;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
