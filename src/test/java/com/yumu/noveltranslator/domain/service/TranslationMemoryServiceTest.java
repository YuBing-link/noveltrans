package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.TranslationMemoryService;

import com.yumu.noveltranslator.domain.model.TranslationMemory;
import com.yumu.noveltranslator.port.out.EmbeddingPort;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TranslationMemoryService 测试")
class TranslationMemoryServiceTest {

    private TranslationMemoryService service;
    private GlossaryRepositoryPort glossaryPort;
    private EmbeddingPort embeddingPort;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        glossaryPort = mock(GlossaryRepositoryPort.class);
        embeddingPort = mock(EmbeddingPort.class);
        service = new TranslationMemoryService(glossaryPort, embeddingPort);
    }

    @Nested
    @DisplayName("storeTranslation 测试")
    class StoreTranslationTests {

        @Test
        void 源文本为空跳过存储() {
            service.storeTranslation(null, "hello", "en", "zh", 1L, null, "google", "fast");
            verifyNoInteractions(embeddingPort);
            verify(glossaryPort, never()).saveTranslationMemory(any());
        }

        @Test
        void 源文本为空白跳过存储() {
            service.storeTranslation("   ", "hello", "en", "zh", 1L, null, "google", "fast");
            verifyNoInteractions(embeddingPort);
            verify(glossaryPort, never()).saveTranslationMemory(any());
        }

        @Test
        void 目标文本为空跳过存储() {
            service.storeTranslation("hello", null, "en", "zh", 1L, null, "google", "fast");
            verifyNoInteractions(embeddingPort);
            verify(glossaryPort, never()).saveTranslationMemory(any());
        }

        @Test
        void 目标文本为空白跳过存储() {
            service.storeTranslation("hello", "  ", "en", "zh", 1L, null, "google", "fast");
            verifyNoInteractions(embeddingPort);
            verify(glossaryPort, never()).saveTranslationMemory(any());
        }

        @Test
        void 存储成功带向量() {
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingPort.embed("hello world")).thenReturn(embedding);

            service.storeTranslation("hello world", "你好世界", "en", "zh", 1L, 100L, "google", "expert");

            verify(embeddingPort).embed("hello world");
            verify(glossaryPort).saveTranslationMemory(argThat(m ->
                m.getUserId().equals(1L) &&
                m.getProjectId().equals(100L) &&
                m.getSourceLang().equals("en") &&
                m.getTargetLang().equals("zh") &&
                m.getSourceText().equals("hello world") &&
                m.getTargetText().equals("你好世界") &&
                m.getSourceEngine().equals("google") &&
                m.getUsageCount() == 0 &&
                m.getEmbedding().size() == 3
            ));
        }

        @Test
        void 向量为空时不设置向量字段() {
            when(embeddingPort.embed("hello")).thenReturn(new float[0]);

            service.storeTranslation("hello", "你好", "en", "zh", 1L, null, "deepl", "fast");

            verify(glossaryPort).saveTranslationMemory(argThat(m -> m.getEmbedding() == null));
        }
    }

    @Nested
    @DisplayName("incrementUsage 测试")
    class IncrementUsageTests {

        @Test
        void 增加使用计数直接调用port() {
            service.incrementUsage(10L);

            verify(glossaryPort).incrementMemoryUsage(10L);
        }
    }

    @Nested
    @DisplayName("searchByUserAndLang 测试")
    class SearchByUserAndLangTests {

        @Test
        void 委托给port查询() {
            List<TranslationMemory> expected = List.of(new TranslationMemory());
            when(glossaryPort.findTopMemoryByUserAndLang(1L, "en", "zh", 10))
                .thenReturn(expected);

            List<TranslationMemory> result = service.searchByUserAndLang(1L, "en", "zh", 10);

            assertEquals(1, result.size());
            verify(glossaryPort).findTopMemoryByUserAndLang(1L, "en", "zh", 10);
        }

        @Test
        void 返回空列表() {
            when(glossaryPort.findTopMemoryByUserAndLang(1L, "en", "zh", 10))
                .thenReturn(List.of());

            List<TranslationMemory> result = service.searchByUserAndLang(1L, "en", "zh", 10);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("searchByProject 测试")
    class SearchByProjectTests {

        @Test
        void 委托给port查询项目记忆() {
            List<TranslationMemory> expected = List.of(new TranslationMemory(), new TranslationMemory());
            when(glossaryPort.findMemoryByProjectId(100L)).thenReturn(expected);

            List<TranslationMemory> result = service.searchByProject(100L);

            assertEquals(2, result.size());
            verify(glossaryPort).findMemoryByProjectId(100L);
        }

        @Test
        void 项目无记忆返回空列表() {
            when(glossaryPort.findMemoryByProjectId(999L)).thenReturn(List.of());

            List<TranslationMemory> result = service.searchByProject(999L);

            assertTrue(result.isEmpty());
        }
    }
}
