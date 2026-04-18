package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitChapterRequest {

    @NotBlank(message = "译文内容不能为空")
    private String translatedText;
}
