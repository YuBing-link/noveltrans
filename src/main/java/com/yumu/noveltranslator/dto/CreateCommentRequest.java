package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {

    private String sourceText;

    private String targetText;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentId;
}
