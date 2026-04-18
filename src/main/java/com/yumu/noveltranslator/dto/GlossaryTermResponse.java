package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 术语项响应（别名，与 GlossaryResponse 相同）
 */
@Data
public class GlossaryTermResponse {
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
