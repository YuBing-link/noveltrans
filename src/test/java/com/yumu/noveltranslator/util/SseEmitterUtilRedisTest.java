package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SseEmitterUtil Redis Stream 测试")
class SseEmitterUtilRedisTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private SseEmitterUtil sseEmitterUtil;

    @BeforeEach
    void setUp() {
        sseEmitterUtil = new SseEmitterUtil(stringRedisTemplate);
    }

    @Nested
    @DisplayName("publishCollabEvent")
    class PublishCollabEventTests {

        @Test
        void 调用RedisScript写入Stream() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn("OK");

            String result = sseEmitterUtil.publishCollabEvent("1", "chapter.submitted",
                    "{\"chapterId\":10,\"userId\":5}");

            assertNotNull(result);
            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    argThat(keys -> keys.contains("collab:events:1")),
                    anyString());
        }

        @Test
        void 事件JSON包含必填字段() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn("OK");

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            sseEmitterUtil.publishCollabEvent("42", "comment.added", "{\"userId\":3}");

            verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), anyList(), payloadCaptor.capture());
            String eventJson = payloadCaptor.getValue();

            assertNotNull(eventJson);
            assertTrue(eventJson.contains("\"eventId\""));
            assertTrue(eventJson.contains("\"type\":\"comment.added\""));
            assertTrue(eventJson.contains("\"payload\""));
            assertTrue(eventJson.contains("\"timestamp\""));
        }

        @Test
        void 使用正确的StreamKey() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn("OK");

            sseEmitterUtil.publishCollabEvent("99", "chapter.updated", "{\"x\":1}");

            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    argThat(keys -> keys.size() == 1 && keys.get(0).equals("collab:events:99")),
                    anyString());
        }
    }

    @Nested
    @DisplayName("replayMissedEvents")
    class ReplayMissedEventsTests {

        @Test
        void nullLastEventId跳过重放() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

            sseEmitterUtil.replayMissedEvents("1", null, emitter);

            verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
        }

        @Test
        void 空字符串LastEventId跳过重放() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

            sseEmitterUtil.replayMissedEvents("1", "", emitter);

            verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
        }

        @Test
        void 空Stream不发送任何事件() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn(List.of());

            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

            sseEmitterUtil.replayMissedEvents("1", "0-0", emitter);

            verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString());
        }

        @Test
        void 从Stream检索事件并发送() throws IOException {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn(List.of(
                            "{\"eventId\":\"e1\",\"type\":\"chapter.updated\",\"payload\":{},\"timestamp\":1000}",
                            "{\"eventId\":\"e2\",\"type\":\"comment.added\",\"payload\":{},\"timestamp\":2000}"));

            SseEmitter emitter = spy(SseEmitterUtil.createSseEmitter(60_000L));

            sseEmitterUtil.replayMissedEvents("1", "0-0", emitter);

            verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        void 使用正确的StreamKey和lastEventId() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                    .thenReturn(List.of());

            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);

            sseEmitterUtil.replayMissedEvents("5", "1234-0", emitter);

            verify(stringRedisTemplate).execute(
                    any(DefaultRedisScript.class),
                    argThat(keys -> keys.contains("collab:events:5")),
                    eq("1234-0"));
        }
    }

    @Nested
    @DisplayName("向后兼容性")
    class BackwardCompatibilityTests {

        @Test
        void 静态方法仍然可用() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(null);
            assertNotNull(emitter);
            assertDoesNotThrow(() -> SseEmitterUtil.sendData(emitter, "test"));
            assertDoesNotThrow(() -> SseEmitterUtil.sendHeartbeat(emitter));
            assertDoesNotThrow(() -> SseEmitterUtil.sendDone(emitter));
            assertDoesNotThrow(() -> SseEmitterUtil.complete(emitter));
        }

        @Test
        void registerEmitter静态方法正常工作() {
            SseEmitter emitter = SseEmitterUtil.createSseEmitter(60_000L);
            String id = SseEmitterUtil.registerEmitter(emitter);
            assertNotNull(id);
            assertTrue(SseEmitterUtil.isEmitterActive(id));
            SseEmitterUtil.complete(emitter);
        }
    }
}
