package com.yumu.noveltranslator.port.dto.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserPreferencesRequest {
    private String defaultEngine;

    private String defaultTargetLang;

    private Boolean enableGlossary;

    private Long defaultGlossaryId;

    private Boolean enableCache;

    private Boolean autoTranslateSelection;

    @Min(8)
    @Max(72)
    private Integer fontSize;

    private String themeMode;
}
