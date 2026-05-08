package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TokenBlacklist {
    private Long id;
    private String token;
    private String email;
    private String reason;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
