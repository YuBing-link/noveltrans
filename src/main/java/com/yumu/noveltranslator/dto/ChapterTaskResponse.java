package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChapterTaskResponse {
    private Long id;
    private Integer chapterNumber;
    private String title;
    private String status;
    private Integer progress;
    private Long assigneeId;
    private String assigneeName;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private Integer sourceWordCount;
    private Integer targetWordCount;
    private LocalDateTime assignedTime;
    private LocalDateTime submittedTime;
    private LocalDateTime reviewedTime;
    private LocalDateTime completedTime;
    private String sourceText;
    private String translatedText;
}
