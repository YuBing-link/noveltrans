package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignChapterRequest {

    @NotNull(message = "译者ID不能为空")
    private Long assigneeId;

    private Long reviewerId;
}
