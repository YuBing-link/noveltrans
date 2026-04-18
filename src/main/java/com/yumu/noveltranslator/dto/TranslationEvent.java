package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应事件包装类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TranslationEvent {
    /**
     * 事件类型：
     * meta: 传回引擎信息、耗时统计
     * delta: 增量译文内容
     * done: 传输完成，包含最终 token 统计
     * error: 发生错误
     */
    private String type;

    // 实际内容（增量文本或错误消息）
    private String content;

    // 附加数据（如 ID, 消耗的 token 数等）
    private Object extra;
}