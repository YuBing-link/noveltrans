package com.yumu.noveltranslator.port.dto.translation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SelectionTranslateResponse {
    private Boolean success;
    private String engine;
    private String translation;
}
