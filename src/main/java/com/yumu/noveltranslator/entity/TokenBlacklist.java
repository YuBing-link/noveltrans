package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Token blacklist entity for revoked JWTs. */
@Data
@TableName("token_blacklist")
public class TokenBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String token;

    private String email;

    private String reason;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
