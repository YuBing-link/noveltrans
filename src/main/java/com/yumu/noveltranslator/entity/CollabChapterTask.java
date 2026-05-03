package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节任务实体
 */
@Data
@TableName("collab_chapter_task")
public class CollabChapterTask {

    @TableId(type = IdType.AUTO)
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

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private LocalDateTime assignedTime;

    private LocalDateTime submittedTime;

    private LocalDateTime reviewedTime;

    private Integer retryCount;

    private LocalDateTime completedTime;

    @TableLogic
    private Integer deleted;
}
