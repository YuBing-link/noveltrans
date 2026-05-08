package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TranslationCache {
    private Long id;
    private String cacheKey;
    private String sourceText;
    private String targetText;
    private String sourceLang;
    private String targetLang;
    private String engine;
    private String mode;
    private Integer version;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
