package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentResponse {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private String sourceText;
    private String targetText;
    private String content;
    private Boolean resolved;
    private LocalDateTime createTime;
    private List<CommentResponse> replies;
}
