package com.yumu.noveltranslator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseEmitterUtil {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterUtil.class);

    public static SseEmitter createSseEmitter(Long timeout) {
        SseEmitter emitter = new SseEmitter(timeout != null ? timeout : 60_000L);
        emitter.onCompletion(() -> {});
        emitter.onTimeout(emitter::complete);
        emitter.onError((ex) -> {
            if (ex != null) {
                String msg = ex.getMessage();
                // 过滤掉正常的连接关闭错误（前端主动关闭、浏览器中断等）
                if (msg != null &&
                    !(msg.contains("aborted") ||
                      msg.contains("中止") ||
                      msg.contains("reset") ||
                      msg.contains("broken pipe") ||
                      msg.contains("client abort"))) {
                    log.warn("SSE emitter error: {}", msg);
                }
            }
            emitter.complete();
        });
        return emitter;
    }

    public static void sendError(SseEmitter emitter, String msg) {
        try {
            // 前端可以通过 data.startsWith("ERROR:") 来判断
            emitter.send(SseEmitter.event().data("ERROR: " + msg));
        } catch (Exception ignored) {}
    }

    public static void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (Exception ignored) {}
    }

    public static void sendData(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception ignored) {}
    }

    public static void complete(SseEmitter emitter) {
        emitter.complete();
    }

    public static void completeWithError(SseEmitter emitter, Exception ex) {
        emitter.completeWithError(ex);
    }
}