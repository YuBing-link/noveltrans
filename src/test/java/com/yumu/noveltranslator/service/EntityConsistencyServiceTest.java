package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.ConsistencyTranslationResult;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityConsistencyServiceTest {

    @Mock
    private DocumentEntityCache documentEntityCache;

    @Mock
    private GlossaryMapper glossaryMapper;

    @Mock
    private UserPreferenceMapper userPreferenceMapper;

    private EntityConsistencyService service;

    @BeforeEach
    void setUp() {
        service = new EntityConsistencyService(documentEntityCache, glossaryMapper, userPreferenceMapper);
        // Use unreachable URL so HTTP calls fail fast for fallback tests
        ReflectionTestUtils.setField(service, "pythonTranslateUrl",
                "http://nonexistent.invalid.host:9999/translate");
    }

    @Nested
    @DisplayName("shouldUseConsistency")
    class ShouldUseConsistencyTests {

        @Test
        void null文本返回false() {
            assertFalse(service.shouldUseConsistency(null));
        }

        @Test
        void 文本少于500字符返回false() {
            assertFalse(service.shouldUseConsistency("a".repeat(499)));
        }

        @Test
        void 文本等于500字符返回true() {
            assertTrue(service.shouldUseConsistency("a".repeat(500)));
        }

        @Test
        void 文本超过500字符返回true() {
            assertTrue(service.shouldUseConsistency("a".repeat(1000)));
        }
    }

    @Nested
    @DisplayName("translateWithConsistency - 无实体")
    class NoEntityTests {

        @Test
        void 无实体且无术语库返回consistencyAppliedFalse() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            // Python service will fail (unreachable host) -> returns empty entities
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
            assertNull(result.getTranslatedText());
        }
    }

    @Nested
    @DisplayName("translateWithConsistency - Python 服务异常")
    class PythonServiceFailureTests {

        @Test
        void Python服务抛异常降级返回consistencyAppliedFalse() {
            // Use an unreachable host to force HTTP failure
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            // HTTP will fail, entity extraction returns empty, so consistencyApplied = false
            assertFalse(result.isConsistencyApplied());
        }

        @Test
        void Python服务返回空跳过实体提取() {
            // Same unreachable host test - confirms the fallback path
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(600), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
        }
    }

    @Nested
    @DisplayName("translateWithConsistency - 术语库")
    class GlossaryTests {

        @Test
        void 用户未启用术语库不加载术语() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            // Without glossary and with unreachable Python service, should return false
            assertFalse(result.isConsistencyApplied());
            verify(glossaryMapper, never()).selectList(any());
        }

        @Test
        void 术语库启用但无匹配术语() {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenReturn(Collections.emptyList());

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
        }

        @Test
        void 术语库查询异常不抛出() {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenThrow(new RuntimeException("DB error"));

            // Should not throw, should handle gracefully
            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            // Even with glossary DB error, still tries entity extraction (fails due to unreachable host)
            assertFalse(result.isConsistencyApplied());
        }
    }

    @Nested
    @DisplayName("实体去重")
    class EntityDeduplicationTests {

        @Test
        void 嵌套实体保留最长匹配() throws Exception {
            java.lang.reflect.Method method = EntityConsistencyService.class
                    .getDeclaredMethod("deduplicateEntities", Map.class, String.class);
            method.setAccessible(true);

            Map<String, String> entities = Map.of(
                    "Harry", "哈利",
                    "Harry Potter", "哈利波ter",
                    "Potter", "波特"
            );
            String sourceText = "Harry Potter went to school";

            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service, entities, sourceText);

            // "Harry Potter" is longest, "Harry" is contained in it, "Potter" is contained in it
            // So only "Harry Potter" should remain
            assertEquals(1, result.size());
            assertTrue(result.containsKey("Harry Potter"));
        }

        @Test
        void 不在原文中的实体被过滤() throws Exception {
            java.lang.reflect.Method method = EntityConsistencyService.class
                    .getDeclaredMethod("deduplicateEntities", Map.class, String.class);
            method.setAccessible(true);

            Map<String, String> entities = Map.of(
                    "ExistingEntity", "已存在实体",
                    "NotInText", "不在文本中"
            );
            String sourceText = "This text contains ExistingEntity only";

            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service, entities, sourceText);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("ExistingEntity"));
            assertFalse(result.containsKey("NotInText"));
        }
    }

    @Nested
    @DisplayName("占位符构建与还原")
    class PlaceholderTests {

        @Test
        void buildMapping生成正确的占位符() throws Exception {
            java.lang.reflect.Method method = EntityConsistencyService.class
                    .getDeclaredMethod("buildMapping", Map.class);
            method.setAccessible(true);

            Map<String, String> translations = Map.of(
                    "Harry", "哈利",
                    "Ron", "罗恩"
            );

            var context = method.invoke(service, translations);

            // Verify context was created
            assertNotNull(context);
            java.lang.reflect.Field mappingsField = context.getClass().getDeclaredField("mappings");
            mappingsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<?> mappings = (List<?>) mappingsField.get(context);
            assertEquals(2, mappings.size());
        }

        @Test
        void replaceEntitiesWithPlaceholders替换实体() throws Exception {
            java.lang.reflect.Method buildMethod = EntityConsistencyService.class
                    .getDeclaredMethod("buildMapping", Map.class);
            buildMethod.setAccessible(true);
            var context = buildMethod.invoke(service, Map.of("Harry", "哈利", "Ron", "罗恩"));

            java.lang.reflect.Method replaceMethod = EntityConsistencyService.class
                    .getDeclaredMethod("replaceEntitiesWithPlaceholders", String.class,
                            Class.forName("com.yumu.noveltranslator.service.EntityConsistencyService$EntityMappingContext"));
            replaceMethod.setAccessible(true);

            String result = (String) replaceMethod.invoke(service, "Harry and Ron went out", context);

            assertTrue(result.contains("[{1}]"));
            assertTrue(result.contains("[{2}]"));
            assertFalse(result.contains("Harry"));
            assertFalse(result.contains("Ron"));
        }

        @Test
        void restorePlaceholders还原占位符() throws Exception {
            java.lang.reflect.Method buildMethod = EntityConsistencyService.class
                    .getDeclaredMethod("buildMapping", Map.class);
            buildMethod.setAccessible(true);
            var context = buildMethod.invoke(service, Map.of("Harry", "哈利"));

            java.lang.reflect.Method restoreMethod = EntityConsistencyService.class
                    .getDeclaredMethod("restorePlaceholders", String.class,
                            Class.forName("com.yumu.noveltranslator.service.EntityConsistencyService$EntityMappingContext"));
            restoreMethod.setAccessible(true);

            String result = (String) restoreMethod.invoke(service, "Hello [{1}] world", context);

            assertEquals("Hello 哈利 world", result);
        }

        @Test
        void 占位符构建与还原闭环() throws Exception {
            // Test the full round-trip: build -> replace -> restore
            java.lang.reflect.Method buildMethod = EntityConsistencyService.class
                    .getDeclaredMethod("buildMapping", Map.class);
            buildMethod.setAccessible(true);
            var context = buildMethod.invoke(service, Map.of("London", "伦敦", "Harry", "哈利"));

            java.lang.reflect.Method replaceMethod = EntityConsistencyService.class
                    .getDeclaredMethod("replaceEntitiesWithPlaceholders", String.class,
                            Class.forName("com.yumu.noveltranslator.service.EntityConsistencyService$EntityMappingContext"));
            replaceMethod.setAccessible(true);

            String original = "Harry lives in London";
            String withPlaceholders = (String) replaceMethod.invoke(service, original, context);
            // After replacement, original entities are gone
            assertFalse(withPlaceholders.contains("Harry"));
            assertFalse(withPlaceholders.contains("London"));

            java.lang.reflect.Method restoreMethod = EntityConsistencyService.class
                    .getDeclaredMethod("restorePlaceholders", String.class,
                            Class.forName("com.yumu.noveltranslator.service.EntityConsistencyService$EntityMappingContext"));
            restoreMethod.setAccessible(true);

            // Simulate translated text with placeholders preserved
            String translated = withPlaceholders; // In real case, Python translates non-placeholder parts
            String restored = (String) restoreMethod.invoke(service, translated, context);
            assertEquals("哈利 lives in 伦敦", restored);
        }
    }
}
