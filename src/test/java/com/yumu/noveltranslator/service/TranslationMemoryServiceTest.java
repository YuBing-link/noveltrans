package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TranslationMemory;
import com.yumu.noveltranslator.mapper.TranslationMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TranslationMemoryService 测试")
class TranslationMemoryServiceTest {

    private TranslationMemoryService service;
    private TranslationMemoryMapper mapper;
    private EmbeddingService embeddingService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mapper = mock(TranslationMemoryMapper.class);
        embeddingService = mock(EmbeddingService.class);
        service = spy(new TranslationMemoryService(mapper, embeddingService));
    }

    @Nested
    @DisplayName("storeTranslation 测试")
    class StoreTranslationTests {

        @Test
        void 源文本为空跳过存储() {
            service.storeTranslation(null, "hello", "en", "zh", 1L, null, "google");
            verifyNoInteractions(embeddingService);
            verify(service, never()).save(any());
        }

        @Test
        void 源文本为空白跳过存储() {
            service.storeTranslation("   ", "hello", "en", "zh", 1L, null, "google");
            verifyNoInteractions(embeddingService);
            verify(service, never()).save(any());
        }

        @Test
        void 目标文本为空跳过存储() {
            service.storeTranslation("hello", null, "en", "zh", 1L, null, "google");
            verifyNoInteractions(embeddingService);
            verify(service, never()).save(any());
        }

        @Test
        void 目标文本为空白跳过存储() {
            service.storeTranslation("hello", "  ", "en", "zh", 1L, null, "google");
            verifyNoInteractions(embeddingService);
            verify(service, never()).save(any());
        }

        @Test
        void 存储成功带向量() {
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingService.embed("hello world")).thenReturn(embedding);
            doAnswer(invocation -> {
                TranslationMemory m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 42L);
                return true;
            }).when(service).save(any(TranslationMemory.class));

            service.storeTranslation("hello world", "你好世界", "en", "zh", 1L, 100L, "google");

            verify(embeddingService).embed("hello world");
            verify(service).save(argThat(m ->
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
            when(embeddingService.embed("hello")).thenReturn(new float[0]);
            doAnswer(invocation -> {
                TranslationMemory m = invocation.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 1L);
                return true;
            }).when(service).save(any(TranslationMemory.class));

            service.storeTranslation("hello", "你好", "en", "zh", 1L, null, "deepl");

            verify(service).save(argThat(m -> m.getEmbedding() == null));
        }
    }

    @Nested
    @DisplayName("incrementUsage 测试")
    class IncrementUsageTests {

        @Test
        void 增加使用计数成功() {
            TranslationMemory memory = new TranslationMemory();
            memory.setId(10L);
            memory.setUsageCount(5);
            doReturn(memory).when(service).getById(10L);
            doReturn(true).when(service).updateById(any(TranslationMemory.class));

            service.incrementUsage(10L);

            assertEquals(6, memory.getUsageCount());
            verify(service).updateById(any(TranslationMemory.class));
        }

        @Test
        void 记忆不存在不更新() {
            doReturn(null).when(service).getById(99L);

            service.incrementUsage(99L);

            verify(service, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("searchByUserAndLang 测试")
    class SearchByUserAndLangTests {

        @Test
        void 委托给mapper查询() {
            List<TranslationMemory> expected = List.of(new TranslationMemory());
            when(mapper.selectTopByUserAndLang(1L, "en", "zh", 10))
                .thenReturn(expected);

            List<TranslationMemory> result = service.searchByUserAndLang(1L, "en", "zh", 10);

            assertEquals(1, result.size());
            verify(mapper).selectTopByUserAndLang(1L, "en", "zh", 10);
        }

        @Test
        void 返回空列表() {
            when(mapper.selectTopByUserAndLang(1L, "en", "zh", 10))
                .thenReturn(List.of());

            List<TranslationMemory> result = service.searchByUserAndLang(1L, "en", "zh", 10);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("searchByProject 测试")
    class SearchByProjectTests {

        @Test
        void 委托给mapper查询项目记忆() {
            List<TranslationMemory> expected = List.of(new TranslationMemory(), new TranslationMemory());
            when(mapper.selectByProjectId(100L)).thenReturn(expected);

            List<TranslationMemory> result = service.searchByProject(100L);

            assertEquals(2, result.size());
            verify(mapper).selectByProjectId(100L);
        }

        @Test
        void 项目无记忆返回空列表() {
            when(mapper.selectByProjectId(999L)).thenReturn(List.of());

            List<TranslationMemory> result = service.searchByProject(999L);

            assertTrue(result.isEmpty());
        }
    }
}
