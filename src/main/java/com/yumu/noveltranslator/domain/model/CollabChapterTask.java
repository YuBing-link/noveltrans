package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabChapterTask {
    private Long id;
    private Long projectId;
    private Integer chapterNumber;
    private String title;
    private String sourceText;
    private String targetText;
    private Long assigneeId;
    private Long reviewerId;
    private String status;
    private String reviewComment;
    private Integer progress;
    private Integer sourceWordCount;
    private Integer targetWordCount;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime assignedTime;
    private LocalDateTime submittedTime;
    private LocalDateTime reviewedTime;
    private Integer retryCount;
    private LocalDateTime completedTime;
    private Integer deleted;
}
