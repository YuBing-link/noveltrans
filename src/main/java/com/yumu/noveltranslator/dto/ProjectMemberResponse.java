package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectMemberResponse {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String avatar;
    private String role;
    private String inviteStatus;
    private LocalDateTime joinedTime;
}
