package com.yumu.noveltranslator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 发射器工具类。
 * 提供静态方法用于基本的 SSE 操作，同时作为 Spring Bean 提供 Redis Stream 支持以实现事件重放。
 */
@Component
public class SseEmitterUtil {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterUtil.class);
    private static final long DEFAULT_TIMEOUT_MS = 300_000L; // 5 分钟，对齐 application.yaml
    private static final int MAX_ACTIVE_SSE = Integer.getInteger("noveltrans.max.sse", 100);

    /**
     * Emitter 注册表：key=唯一ID，value=可取消的 future
     */
    private static final Map<String, CompletableFuture<Void>> EMITTER_REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE_COUNT = new AtomicInteger(0);

    private final StringRedisTemplate stringRedisTemplate;

    public SseEmitterUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 静态方法（保持向后兼容） ====================

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

    // ==================== 实例方法（Redis Stream 支持） ====================

    /**
     * XADD Lua 脚本：向 Redis Stream 添加事件
     */
    private static final String XADD_LUA =
            "redis.call('XADD', KEYS[1], '*', 'event', ARGV[1]); return 'OK'";

    /**
     * XRANGE Lua 脚本：从 Redis Stream 获取 lastEventId 之后的所有事件
     */
    private static final String XRANGE_LUA =
            "local results = redis.call('XRANGE', KEYS[1], '(' .. ARGV[1], '+'); " +
            "local out = {}; " +
            "for i = 1, #results, 1 do " +
            "  out[i] = results[i][2][2]; " +
            "end; " +
            "return out";

    /**
     * 发布协作事件到 Redis Stream，支持断线重连后补发。
     *
     * @param projectId 项目ID
     * @param eventType 事件类型（如 chapter.updated, comment.added）
     * @param payload   事件载荷（JSON 字符串）
     * @return 生成的事件 ID
     */
    public String publishCollabEvent(String projectId, String eventType, String payload) {
        String streamKey = "collab:events:" + projectId;
        String eventId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        String eventJson = String.format(
                "{\"eventId\":\"%s\",\"type\":\"%s\",\"payload\":%s,\"timestamp\":%d}",
                eventId, eventType, payload, timestamp);

        DefaultRedisScript<String> script = new DefaultRedisScript<>(XADD_LUA, String.class);
        stringRedisTemplate.execute(script, List.of(streamKey), eventJson);

        log.debug("发布协作事件: projectId={}, type={}, eventId={}", projectId, eventType, eventId);
        return eventId;
    }

    /**
     * 从 Redis Stream 重放指定项目自 lastEventId 之后的遗漏事件。
     *
     * @param projectId   项目ID
     * @param lastEventId 上次收到的事件 ID（Redis Stream 条目 ID），为 null 时不重放
     * @param emitter     SSE emitter，用于发送事件
     */
    public void replayMissedEvents(String projectId, String lastEventId, SseEmitter emitter) {
        if (lastEventId == null || lastEventId.isBlank()) {
            log.debug("无 lastEventId，跳过事件重放: projectId={}", projectId);
            return;
        }

        String streamKey = "collab:events:" + projectId;
        DefaultRedisScript<List> script = new DefaultRedisScript<>(XRANGE_LUA, List.class);
        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) stringRedisTemplate.execute(script, List.of(streamKey), lastEventId);

        if (events == null || events.isEmpty()) {
            log.debug("无遗漏事件需要重放: projectId={}, lastEventId={}", projectId, lastEventId);
            return;
        }

        int sent = 0;
        for (String eventJson : events) {
            if (eventJson == null || eventJson.isEmpty()) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("replay")
                        .data(eventJson));
                sent++;
            } catch (Exception e) {
                log.warn("重放事件失败: projectId={}, error={}", projectId, e.getMessage());
                break;
            }
        }

        log.info("重放完成: projectId={}, sent={} events", projectId, sent);
    }
}
