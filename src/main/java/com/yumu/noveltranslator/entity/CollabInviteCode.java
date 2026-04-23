package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邀请码实体
 */
@Data
@TableName("collab_invite_code")
public class CollabInviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String code;

    private LocalDateTime expiresAt;

    /** 是否已使用: 0-未使用, 1-已使用 */
    private Integer used;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
