package com.yumu.noveltranslator.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * AI 翻译团队响应 DTO
 * Python 端返回 snake_case JSON 字段，需通过 @JSONField 映射到 Java camelCase
 */
@Data
public class TeamTranslateResponse {

    /**
     * 状态码（0 表示成功）
     */
    private int code;

    /**
     * 翻译后的文本
     */
    private String data;

    /**
     * 耗时（毫秒）
     */
    @JSONField(name = "cost_ms")
    private double costMs;

    /**
     * 小说类型
     */
    @JSONField(name = "novel_type")
    private String novelType;

    /**
     * 分块数量
     */
    @JSONField(name = "chunk_count")
    private int chunkCount;
}
