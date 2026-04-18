package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReaderTranslateResponse {
    private Boolean success;
    private String engine;
    private String translatedContent;
}
