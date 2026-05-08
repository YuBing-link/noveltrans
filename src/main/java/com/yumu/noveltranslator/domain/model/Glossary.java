package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Glossary {
    private Long id;
    private Long userId;
    private String sourceWord;
    private String targetWord;
    private String remark;
    private Long tenantId;
    private LocalDateTime createTime;
    private Integer deleted;
}
