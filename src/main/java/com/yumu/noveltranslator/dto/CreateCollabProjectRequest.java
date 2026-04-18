package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCollabProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 255, message = "项目名称不能超过255个字符")
    private String name;

    @Size(max = 2000, message = "项目描述不能超过2000个字符")
    private String description;

    @NotBlank(message = "源语言不能为空")
    private String sourceLang;

    @NotBlank(message = "目标语言不能为空")
    private String targetLang;
}
