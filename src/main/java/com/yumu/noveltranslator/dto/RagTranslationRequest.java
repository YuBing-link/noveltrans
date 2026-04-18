package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagTranslationRequest {

    @NotBlank(message = "翻译文本不能为空")
    private String text;

    @NotBlank(message = "目标语言不能为空")
    private String targetLang;

    private String sourceLang;

    private String engine;
}
