package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SelectionTranslateResponse {
    private Boolean success;
    private String engine;
    private String translation;
}
