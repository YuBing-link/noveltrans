package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewChapterRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Size(max = 2000, message = "审核意见不能超过2000个字符")
    private String comment;
}
