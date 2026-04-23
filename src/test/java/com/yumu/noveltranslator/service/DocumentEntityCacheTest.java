package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.ChapterEntityMap;
import com.yumu.noveltranslator.mapper.ChapterEntityMapMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DocumentEntityCache 完整测试")
class DocumentEntityCacheTest {

    private DocumentEntityCache cache;
    private ChapterEntityMapMapper mapper;

    @BeforeEach
    void setUp() {
        cache = new DocumentEntityCache();
        mapper = mock(ChapterEntityMapMapper.class);
        ReflectionTestUtils.setField(cache, "chapterEntityMapMapper", mapper);
    }

    @Nested
    @DisplayName("getEntityMap 测试")
    class GetEntityMapTests {

        @Test
        void 未知key返回空map() {
            assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
        }

        @Test
        void merge后get返回数据() {
            cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
            Map<String, String> map = cache.getEntityMap(1L, "doc-001");
            assertEquals("你好", map.get("Hello"));
        }

        @Test
        void merge多次数据不丢失() {
            cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
            cache.mergeEntityMap(1L, "doc-001", Map.of("World", "世界"));
            Map<String, String> map = cache.getEntityMap(1L, "doc-001");
            assertEquals(2, map.size());
            assertEquals("你好", map.get("Hello"));
            assertEquals("世界", map.get("World"));
        }

        @Test
        void clear后数据消失() {
            cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
            cache.clear(1L, "doc-001");
            assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
        }

        @Test
        void 返回不可变map() {
            cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
            Map<String, String> map = cache.getEntityMap(1L, "doc-001");
            assertThrows(UnsupportedOperationException.class, () -> map.put("New", "新"));
        }

        @Test
        void 数据库查询成功回填缓存() {
            ChapterEntityMap m1 = new ChapterEntityMap();
            m1.setSourceEntity("Dragon");
            m1.setTargetEntity("龙");
            ChapterEntityMap m2 = new ChapterEntityMap();
            m2.setSourceEntity("Sword");
            m2.setTargetEntity("剑");
            when(mapper.selectByChapterId(1L)).thenReturn(List.of(m1, m2));

            Map<String, String> map = cache.getEntityMap(1L, "1");

            assertEquals(2, map.size());
            assertEquals("龙", map.get("Dragon"));
            assertEquals("剑", map.get("Sword"));
            verify(mapper).selectByChapterId(1L);
            // 第二次调用不应再查DB
            cache.getEntityMap(1L, "1");
            verify(mapper, times(1)).selectByChapterId(1L);
        }

        @Test
        void 数据库查询为空返回空map() {
            when(mapper.selectByChapterId(1L)).thenReturn(List.of());

            Map<String, String> map = cache.getEntityMap(1L, "1");

            assertTrue(map.isEmpty());
        }

        @Test
        void 数据库查询异常降级返回空map() {
            when(mapper.selectByChapterId(1L)).thenThrow(new RuntimeException("DB error"));

            Map<String, String> map = cache.getEntityMap(1L, "1");

            assertTrue(map.isEmpty());
        }

        @Test
        void documentId非数字不走数据库查询() {
            Map<String, String> map = cache.getEntityMap(1L, "doc-abc");

            assertTrue(map.isEmpty());
            verify(mapper, never()).selectByChapterId(anyLong());
        }

        @Test
        void documentId为null返回空map() {
            Map<String, String> map = cache.getEntityMap(1L, null);
            assertTrue(map.isEmpty());
        }

        @Test
        void documentId为空白返回空map() {
            Map<String, String> map = cache.getEntityMap(1L, "   ");
            assertTrue(map.isEmpty());
        }
    }

    @Nested
    @DisplayName("mergeEntityMap 测试")
    class MergeEntityMapTests {

        @Test
        void null映射直接返回() {
            cache.mergeEntityMap(1L, "doc-001", null);
            assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
        }

        @Test
        void 空映射直接返回() {
            cache.mergeEntityMap(1L, "doc-001", Map.of());
            assertTrue(cache.getEntityMap(1L, "doc-001").isEmpty());
        }

        @Test
        void 数据库写入异常不抛异常() {
            doThrow(new RuntimeException("DB write failed")).when(mapper).insert(any());

            cache.mergeEntityMap(1L, "1", Map.of("Hello", "你好"));

            // 缓存仍然写入成功
            assertEquals("你好", cache.getEntityMap(1L, "1").get("Hello"));
        }

        @Test
        void 合并到缓存并尝试写数据库() {
            cache.mergeEntityMap(1L, "1", Map.of("Name", "名字"));

            assertEquals("名字", cache.getEntityMap(1L, "1").get("Name"));
            verify(mapper).insert(argThat(m ->
                m.getChapterId().equals(1L) &&
                m.getSourceEntity().equals("Name") &&
                m.getTargetEntity().equals("名字")
            ));
        }
    }

    @Nested
    @DisplayName("saveEntityMapping 测试")
    class SaveEntityMappingTests {

        @Test
        void null映射直接返回() {
            cache.saveEntityMapping(1L, 100L, null);
            verify(mapper, never()).insert(any());
        }

        @Test
        void 空映射直接返回() {
            cache.saveEntityMapping(1L, 100L, Map.of());
            verify(mapper, never()).insert(any());
        }

        @Test
        void 批量保存映射() {
            cache.saveEntityMapping(1L, 100L, Map.of(
                "Dragon", "龙",
                "Sword", "剑"
            ));

            verify(mapper, times(2)).insert(any(ChapterEntityMap.class));
        }

        @Test
        void projectId为null也能保存() {
            cache.saveEntityMapping(1L, null, Map.of("Key", "值"));

            verify(mapper).insert(argThat(m ->
                m.getChapterId().equals(1L) &&
                m.getProjectId() == null &&
                m.getSourceEntity().equals("Key")
            ));
        }
    }

    @Nested
    @DisplayName("clear 测试")
    class ClearTests {

        @Test
        void 清除不存在的key无影响() {
            cache.clear(1L, "non-existent");
            assertTrue(cache.getEntityMap(1L, "non-existent").isEmpty());
        }

        @Test
        void 清除后不同userId不受影响() {
            cache.mergeEntityMap(1L, "doc-001", Map.of("Hello", "你好"));
            cache.clear(2L, "doc-001");
            // userId=1 的数据仍然存在
            assertEquals("你好", cache.getEntityMap(1L, "doc-001").get("Hello"));
        }
    }
}
