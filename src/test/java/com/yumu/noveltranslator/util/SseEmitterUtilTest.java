package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SseEmitterUtil 测试")
class SseEmitterUtilTest {

    @Test
    @DisplayName("创建SseEmitter使用默认超时")
    void createSseEmitterWithDefaultTimeout() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(null);

        assertNotNull(emitter);
    }

    @Test
    @DisplayName("创建SseEmitter使用指定超时")
    void createSseEmitterWithCustomTimeout() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(30_000L);

        assertNotNull(emitter);
    }

    @Test
    @DisplayName("sendData发送数据不抛异常")
    void sendDataDoesNotThrow() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

        // Should not throw - exception is caught internally
        assertDoesNotThrow(() -> SseEmitterUtil.sendData(emitter, "test data"));
    }

    @Test
    @DisplayName("sendDone发送完成标记")
    void sendDoneDoesNotThrow() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

        assertDoesNotThrow(() -> SseEmitterUtil.sendDone(emitter));
    }

    @Test
    @DisplayName("sendError发送错误消息")
    void sendErrorDoesNotThrow() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

        assertDoesNotThrow(() -> SseEmitterUtil.sendError(emitter, "some error"));
    }

    @Test
    @DisplayName("complete正常完成")
    void complete() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

        assertDoesNotThrow(() -> SseEmitterUtil.complete(emitter));
    }

    @Test
    @DisplayName("completeWithError携带异常")
    void completeWithError() {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
        Exception ex = new RuntimeException("test error");

        assertDoesNotThrow(() -> SseEmitterUtil.completeWithError(emitter, ex));
    }
}
