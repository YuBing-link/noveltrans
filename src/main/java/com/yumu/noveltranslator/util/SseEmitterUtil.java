package com.yumu.noveltranslator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SseEmitterUtil {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterUtil.class);
    private static final long DEFAULT_TIMEOUT_MS = 300_000L; // 5 分钟，对齐 application.yaml
    private static final int MAX_ACTIVE_SSE = Integer.getInteger("noveltrans.max.sse", 100);

    /**
     * Emitter 注册表：key=唯一ID，value=可取消的 future
     */
    private static final Map<String, CompletableFuture<Void>> EMITTER_REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE_COUNT = new AtomicInteger(0);

    /**
     * 注册 emitter，返回唯一 ID（用于心跳循环）
     * @throws IllegalStateException 如果超过最大连接数
     */
    public static String registerEmitter(SseEmitter emitter) {
        int count = ACTIVE_COUNT.get();
        if (count >= MAX_ACTIVE_SSE) {
            throw new IllegalStateException("SSE 连接数已达上限: " + MAX_ACTIVE_SSE);
        }

        String emitterId = UUID.randomUUID().toString();
        CompletableFuture<Void> cancellationSignal = new CompletableFuture<>();
        EMITTER_REGISTRY.put(emitterId, cancellationSignal);
        ACTIVE_COUNT.incrementAndGet();

        emitter.onCompletion(() -> {
            unregisterEmitter(emitterId);
        });
        emitter.onTimeout(() -> {
            unregisterEmitter(emitterId);
            emitter.complete();
        });
        emitter.onError((ex) -> {
            if (ex != null) {
                String msg = ex.getMessage();
                if (msg != null &&
                    !(msg.contains("aborted") ||
                      msg.contains("中止") ||
                      msg.contains("reset") ||
                      msg.contains("broken pipe") ||
                      msg.contains("client abort"))) {
                    log.warn("SSE emitter error: {}", msg);
                }
            }
            unregisterEmitter(emitterId);
            emitter.complete();
        });

        return emitterId;
    }

    /**
     * 检查 emitter 是否仍然活跃（心跳循环用）
     */
    public static boolean isEmitterActive(String emitterId) {
        CompletableFuture<Void> future = EMITTER_REGISTRY.get(emitterId);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * 注销 emitter（内部调用）
     */
    private static void unregisterEmitter(String emitterId) {
        CompletableFuture<Void> future = EMITTER_REGISTRY.remove(emitterId);
        if (future != null) {
            future.complete(null);
        }
        ACTIVE_COUNT.decrementAndGet();
    }

    /**
     * 获取当前活跃的 SSE 连接数（监控用）
     */
    public static int getActiveCount() {
        return ACTIVE_COUNT.get();
    }

    /**
     * 创建 SSE emitter（简单版，不注册到注册表）
     * 适用于不需要心跳管理的场景
     */
    public static SseEmitter createSseEmitter(Long timeout) {
        SseEmitter emitter = new SseEmitter(timeout != null ? timeout : DEFAULT_TIMEOUT_MS);
        emitter.onCompletion(() -> {});
        emitter.onTimeout(emitter::complete);
        emitter.onError((ex) -> {
            if (ex != null) {
                String msg = ex.getMessage();
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

    /**
     * 发送心跳事件，防止长翻译过程中 SSE 连接因超时而断开。
     * 使用 SSE comment 格式（冒号前缀），前端会自动忽略此类注释行。
     */
    public static void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception ignored) {}
    }

    public static void complete(SseEmitter emitter) {
        emitter.complete();
    }

    public static void completeWithError(SseEmitter emitter, Exception ex) {
        emitter.completeWithError(ex);
    }
}
