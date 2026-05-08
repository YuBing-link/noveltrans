package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class User {
    private Long id;
    private String email;
    private String username;
    private String avatar;
    private String password;
    private Map<String, String> apiKey;
    private String refreshToken;
    private String userLevel;
    private String status;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginTime;
    private Integer deleted;
}
