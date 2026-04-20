package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 协作项目实体
 */
@Data
@TableName("collab_project")
public class CollabProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    /** 关联的上传文档ID */
    private Long documentId;

    private String sourceLang;

    private String targetLang;

    private String status;

    private Integer progress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
