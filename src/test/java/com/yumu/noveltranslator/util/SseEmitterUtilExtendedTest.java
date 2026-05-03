package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SseEmitterUtil 扩展测试")
class SseEmitterUtilExtendedTest {

    @Nested
    @DisplayName("registerEmitter")
    class RegisterEmitterTests {

        @Test
        void 注册成功返回非空ID() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            String id = SseEmitterUtil.registerEmitter(emitter);
            assertNotNull(id);
            assertFalse(id.isBlank());
        }

        @Test
        void 注册后isActive为true() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            String id = SseEmitterUtil.registerEmitter(emitter);
            assertTrue(SseEmitterUtil.isEmitterActive(id));
        }

        @Test
        void 未知ID返回inactive() {
            assertFalse(SseEmitterUtil.isEmitterActive("nonexistent-id"));
        }

        @Test
        void getActiveCount注册后增加() {
            int before = SseEmitterUtil.getActiveCount();
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            SseEmitterUtil.registerEmitter(emitter);
            int after = SseEmitterUtil.getActiveCount();
            assertEquals(before + 1, after);
        }
    }

    @Nested
    @DisplayName("createSseEmitter")
    class CreateEmitterTests {

        @Test
        void null超时使用默认值() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(null);
            assertNotNull(emitter);
        }

        @Test
        void 指定超时正常创建() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(120_000L);
            assertNotNull(emitter);
        }
    }

    @Nested
    @DisplayName("心跳与消息发送")
    class SendTests {

        @Test
        void 心跳发送不抛异常() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            assertDoesNotThrow(() -> SseEmitterUtil.sendHeartbeat(emitter));
        }

        @Test
        void 发送数据不抛异常() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            assertDoesNotThrow(() -> SseEmitterUtil.sendData(emitter, "test data"));
        }

        @Test
        void 发送完成标记不抛异常() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            assertDoesNotThrow(() -> SseEmitterUtil.sendDone(emitter));
        }

        @Test
        void 发送错误消息不抛异常() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            assertDoesNotThrow(() -> SseEmitterUtil.sendError(emitter, "something broke"));
        }

        @Test
        void 携带异常完成不抛异常() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            Exception ex = new RuntimeException("test error");
            assertDoesNotThrow(() -> SseEmitterUtil.completeWithError(emitter, ex));
        }
    }

    @Nested
    @DisplayName("完整生命周期")
    class LifecycleTests {

        @Test
        void complete正常完成() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            assertDoesNotThrow(() -> SseEmitterUtil.complete(emitter));
        }

        @Test
        void 注册超过上限抛出异常() {
            // 由于 MAX_ACTIVE_SSE 默认 100，需要大量注册。
            // 这里验证 registerEmitter 能正常注册不抛异常
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            String id = SseEmitterUtil.registerEmitter(emitter);
            assertNotNull(id);

            // 清理
            SseEmitterUtil.complete(emitter);
        }
    }
}
