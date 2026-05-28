package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.translation.StreamTranslateEvent;
import java.io.IOException;

/**
 * 流式翻译事件回调 — port 层接口，不依赖 Spring 类型
 * Service 层通过此接口推送翻译事件，由 Controller 适配为 SseEmitter
 */
@FunctionalInterface
public interface StreamTranslateEventConsumer {
    void accept(StreamTranslateEvent event) throws IOException;
}
