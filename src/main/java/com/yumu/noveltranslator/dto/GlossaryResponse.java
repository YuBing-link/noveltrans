package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 术语项响应（对应 glossary 表中的单条术语记录）
 */
@Data
public class GlossaryResponse {
    /**
     * 术语项 ID
     */
    private Long id;

    /**
     * 待替换的原词
     */
    private String sourceWord;

    /**
     * 替换后的译词
     */
    private String targetWord;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
