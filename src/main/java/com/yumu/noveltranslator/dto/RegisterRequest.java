package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
public class RegisterRequest {
    /**
     * 用户邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 用户密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于 6 位")
    private String password;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 用户名（可选）
     */
    private String username;

    /**
     * 头像 URL（可选）
     */
    private String avatar;
}
