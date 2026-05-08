package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CollabProjectMember {
    private Long id;
    private Long projectId;
    private Long userId;
    private String role;
    private String inviteCode;
    private String inviteStatus;
    private LocalDateTime joinedTime;
    private Long tenantId;
    private LocalDateTime createTime;
    private Integer deleted;
}
