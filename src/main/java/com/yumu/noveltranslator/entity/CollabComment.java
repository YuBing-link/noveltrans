package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 协作评论实体
 */
@Data
@TableName("collab_comment")
public class CollabComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterTaskId;

    private Long userId;

    private String sourceText;

    private String targetText;

    private String content;

    private Long parentId;

    private Boolean resolved;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
