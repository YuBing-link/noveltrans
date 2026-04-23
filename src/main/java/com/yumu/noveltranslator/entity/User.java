package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户实体类
 */
@Data
@TableName(value = "user", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户邮箱，对应唯一索引 idx_user_email
     */
    private String email;

    /**
     * 用户名
     */
    private String username;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 用户密码（BCrypt 加密）
     */
    private String password;

    /**
     * API Key（JSON 格式存储）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> apiKey;

    
    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 用户等级 (FREE, PRO)
     */
    private String userLevel;

    /**
     * 账户状态 (ACTIVE, LOCKED, DISABLED)
     */
    private String status;

    /**
     * 租户 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
