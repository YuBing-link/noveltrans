package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WebpageTranslateResponse {
    private Boolean success;
    private String engine;
    private List<Translation> translations;

    @Data
    @AllArgsConstructor
    public static class Translation {
        private String textId;
        private String original;
        private String translation;
    }
}
