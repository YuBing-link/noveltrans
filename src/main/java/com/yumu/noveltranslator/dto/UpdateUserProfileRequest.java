package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 更新用户信息请求
 */
@Data
public class UpdateUserProfileRequest {
    /**
     * 新用户名
     */
    private String username;

    /**
     * 头像 URL
     */
    private String avatar;
}
