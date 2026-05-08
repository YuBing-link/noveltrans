package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserPreference {
    private Long id;
    private Long userId;
    private String defaultEngine;
    private String defaultTargetLang;
    private Boolean enableGlossary;
    private Long defaultGlossaryId;
    private Boolean enableCache;
    private Boolean autoTranslateSelection;
    private Integer fontSize;
    private String themeMode;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
