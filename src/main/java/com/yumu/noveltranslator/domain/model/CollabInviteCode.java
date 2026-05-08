package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabInviteCode {
    private Long id;
    private Long projectId;
    private String code;
    private LocalDateTime expiresAt;
    private Integer used;
    private Long tenantId;
    private LocalDateTime createTime;
    private Integer deleted;
}
