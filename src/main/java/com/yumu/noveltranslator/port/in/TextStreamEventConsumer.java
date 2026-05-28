package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.translation.TextStreamEvent;
import java.io.IOException;

/**
 * 文本流式翻译事件回调 — port 层接口，不依赖 Spring 类型
 */
@FunctionalInterface
public interface TextStreamEventConsumer {
    void accept(TextStreamEvent event) throws IOException;
}
