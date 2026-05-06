package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.CollabEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.util.SseEmitterUtil;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CollabEventPublisher 测试")
class CollabEventPublisherTest {

    @Mock
    private SseEmitterUtil sseEmitterUtil;

    private CollabEventPublisher collabEventPublisher;

    @BeforeEach
    void setUp() {
        collabEventPublisher = new CollabEventPublisher(sseEmitterUtil, new ObjectMapper());
    }

    @Nested
    @DisplayName("publishChapterUpdate")
    class PublishChapterUpdateTests {

        @Test
        void 发布章节更新事件() {
            when(sseEmitterUtil.publishCollabEvent(any(), any(), any())).thenReturn("1-0");

            collabEventPublisher.publishChapterUpdate(1L, 10L, "5", "updated");

            ArgumentCaptor<String> projectIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            verify(sseEmitterUtil).publishCollabEvent(
                    projectIdCaptor.capture(), eventTypeCaptor.capture(), payloadCaptor.capture());

            assertEquals("1", projectIdCaptor.getValue());
            assertEquals("chapter.updated", eventTypeCaptor.getValue());
            String payload = payloadCaptor.getValue();
            assertTrue(payload.contains("\"chapterId\":10"));
            assertTrue(payload.contains("\"userId\":\"5\""));
            assertTrue(payload.contains("\"action\":\"updated\""));
        }

        @Test
        void 发布章节分配事件() {
            when(sseEmitterUtil.publishCollabEvent(any(), any(), any())).thenReturn("1-0");

            collabEventPublisher.publishChapterUpdate(2L, 20L, "3", "assigned");

            verify(sseEmitterUtil).publishCollabEvent(eq("2"), eq("chapter.assigned"), any());
        }

        @Test
        void 发布章节提交事件() {
            when(sseEmitterUtil.publishCollabEvent(any(), any(), any())).thenReturn("1-0");

            collabEventPublisher.publishChapterUpdate(3L, 30L, "7", "submitted");

            verify(sseEmitterUtil).publishCollabEvent(eq("3"), eq("chapter.submitted"), any());
        }
    }

    @Nested
    @DisplayName("publishCommentAdded")
    class PublishCommentAddedTests {

        @Test
        void 发布评论添加事件() {
            when(sseEmitterUtil.publishCollabEvent(any(), any(), any())).thenReturn("1-0");

            collabEventPublisher.publishCommentAdded(1L, 10L, 5L, "这段翻译很好");

            ArgumentCaptor<String> projectIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            verify(sseEmitterUtil).publishCollabEvent(
                    projectIdCaptor.capture(), eventTypeCaptor.capture(), payloadCaptor.capture());

            assertEquals("1", projectIdCaptor.getValue());
            assertEquals("comment.added", eventTypeCaptor.getValue());
            String payload = payloadCaptor.getValue();
            assertTrue(payload.contains("\"chapterTaskId\":10"));
            assertTrue(payload.contains("\"userId\":5"));
            assertTrue(payload.contains("\"content\":\"这段翻译很好\""));
        }

        @Test
        void 发布空内容评论事件() {
            when(sseEmitterUtil.publishCollabEvent(any(), any(), any())).thenReturn("1-0");

            collabEventPublisher.publishCommentAdded(1L, 10L, 5L, "");

            verify(sseEmitterUtil).publishCollabEvent(eq("1"), eq("comment.added"), any());
        }
    }
}
