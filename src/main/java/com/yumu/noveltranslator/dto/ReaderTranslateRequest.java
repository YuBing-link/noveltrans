package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReaderTranslateRequest {
    @NotBlank(message = "翻译内容不能为空")
    private String content;

    @NotBlank(message = "目标语言不能为空")
    private String targetLang;

    private String sourceLang;

    private String engine;
}
