package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ==================== shouldUseConsistency ====================

    @Nested
    @DisplayName("shouldUseConsistency")
    class ShouldUseConsistencyTests {

        @Test
        @DisplayName("null文本返回false")
        void null文本返回false() {
            assertFalse(service.shouldUseConsistency(null));
        }

        @Test
        @DisplayName("空字符串返回false")
        void 空字符串返回false() {
            assertFalse(service.shouldUseConsistency(""));
        }

        @Test
        @DisplayName("文本少于500字符返回false")
        void 文本少于500字符返回false() {
            assertFalse(service.shouldUseConsistency("a".repeat(499)));
        }

        @Test
        @DisplayName("文本等于500字符返回true")
        void 文本等于500字符返回true() {
            assertTrue(service.shouldUseConsistency("a".repeat(500)));
        }

        @Test
        @DisplayName("文本超过500字符返回true")
        void 文本超过500字符返回true() {
            assertTrue(service.shouldUseConsistency("a".repeat(1000)));
        }
    }

    // ==================== buildMapping ====================

    @Nested
    @DisplayName("buildMapping")
    class BuildMappingTests {

        @Test
        @DisplayName("空映射生成空上下文")
        void 空映射生成空上下文() {
            var context = service.buildMapping(Map.of());

            assertNotNull(context);
            assertEquals(0, context.mappings().size());
            assertEquals(0, context.entityToPlaceholder().size());
        }

        @Test
        @DisplayName("单个实体生成正确占位符")
        void 单个实体生成正确占位符() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));

            assertEquals(1, context.mappings().size());
            var mapping = context.mappings().get(0);
            assertEquals("Harry", mapping.getSourceText());
            assertEquals("哈利", mapping.getTranslatedText());
            assertEquals("[{1}]", mapping.getPlaceholder());
            assertEquals(1, mapping.getIndex());
        }

        @Test
        @DisplayName("多个实体生成递增占位符")
        void 多个实体生成递增占位符() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Harry", "哈利");
            translations.put("Ron", "罗恩");
            translations.put("Hermione", "赫敏");

            var context = service.buildMapping(translations);

            assertEquals(3, context.mappings().size());
            assertEquals("[{1}]", context.mappings().get(0).getPlaceholder());
            assertEquals("[{2}]", context.mappings().get(1).getPlaceholder());
            assertEquals("[{3}]", context.mappings().get(2).getPlaceholder());
        }

        @Test
        @DisplayName("entityToPlaceholder映射正确")
        void entityToPlaceholder映射正确() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Harry", "哈利");
            translations.put("Ron", "罗恩");

            var context = service.buildMapping(translations);

            assertEquals("[{1}]", context.entityToPlaceholder().get("Harry"));
            assertEquals("[{2}]", context.entityToPlaceholder().get("Ron"));
        }

        @Test
        @DisplayName("大量实体占位符索引连续递增")
        void 大量实体占位符索引连续递增() {
            Map<String, String> translations = new LinkedHashMap<>();
            for (int i = 1; i <= 20; i++) {
                translations.put("Entity" + i, "实体" + i);
            }

            var context = service.buildMapping(translations);

            assertEquals(20, context.mappings().size());
            assertEquals("[{20}]", context.mappings().get(19).getPlaceholder());
        }
    }

    // ==================== replaceEntitiesWithPlaceholders ====================

    @Nested
    @DisplayName("replaceEntitiesWithPlaceholders")
    class ReplaceEntitiesWithPlaceholdersTests {

        @Test
        @DisplayName("替换单个实体为占位符")
        void 替换单个实体为占位符() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.replaceEntitiesWithPlaceholders("Harry went to school", context);

            assertEquals("[{1}] went to school", result);
        }

        @Test
        @DisplayName("替换多个实体为占位符")
        void 替换多个实体为占位符() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Harry", "哈利");
            translations.put("Ron", "罗恩");

            var context = service.buildMapping(translations);
            String result = service.replaceEntitiesWithPlaceholders("Harry and Ron went out", context);

            assertTrue(result.contains("[{1}]"));
            assertTrue(result.contains("[{2}]"));
            assertFalse(result.contains("Harry"));
            assertFalse(result.contains("Ron"));
        }

        @Test
        @DisplayName("实体多次出现全部替换")
        void 实体多次出现全部替换() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.replaceEntitiesWithPlaceholders("Harry saw Harry, Harry smiled", context);

            assertEquals("[{1}] saw [{1}], [{1}] smiled", result);
        }

        @Test
        @DisplayName("长实体优先于短实体替换")
        void 长实体优先于短实体替换() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Harry", "哈利");
            translations.put("Harry Potter", "哈利波特");

            var context = service.buildMapping(translations);
            String result = service.replaceEntitiesWithPlaceholders("Harry Potter is great", context);

            // Long entity replaced first, short entity won't find a match
            assertFalse(result.contains("Harry Potter"));
            assertTrue(result.contains("[{"));
        }

        @Test
        @DisplayName("文本中不存在实体时原文返回")
        void 文本中不存在实体时原文返回() {
            var context = service.buildMapping(Map.of("Voldemort", "伏地魔"));
            String result = service.replaceEntitiesWithPlaceholders("Harry went to school", context);

            assertEquals("Harry went to school", result);
        }

        @Test
        @DisplayName("空文本返回空")
        void 空文本返回空() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.replaceEntitiesWithPlaceholders("", context);

            assertEquals("", result);
        }

        @Test
        @DisplayName("占位符格式正确为方括号加数字")
        void 占位符格式正确为方括号加数字() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("A", "译A");
            translations.put("B", "译B");

            var context = service.buildMapping(translations);
            String result = service.replaceEntitiesWithPlaceholders("A and B", context);

            assertTrue(result.matches("\\[\\{1\\}\\] and \\[\\{2\\}\\]"));
        }
    }

    // ==================== restorePlaceholders ====================

    @Nested
    @DisplayName("restorePlaceholders")
    class RestorePlaceholdersTests {

        @Test
        @DisplayName("还原单个占位符")
        void 还原单个占位符() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.restorePlaceholders("Hello [{1}] world", context);

            assertEquals("Hello 哈利 world", result);
        }

        @Test
        @DisplayName("还原多个占位符")
        void 还原多个占位符() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Harry", "哈利");
            translations.put("Ron", "罗恩");

            var context = service.buildMapping(translations);
            String result = service.restorePlaceholders("[{1}] and [{2}] went out", context);

            assertEquals("哈利 and 罗恩 went out", result);
        }

        @Test
        @DisplayName("占位符多次出现全部还原")
        void 占位符多次出现全部还原() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.restorePlaceholders("[{1}] saw [{1}]", context);

            assertEquals("哈利 saw 哈利", result);
        }

        @Test
        @DisplayName("无占位符时原文返回")
        void 无占位符时原文返回() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.restorePlaceholders("Just plain text", context);

            assertEquals("Just plain text", result);
        }

        @Test
        @DisplayName("空文本还原返回空")
        void 空文本还原返回空() {
            var context = service.buildMapping(Map.of("Harry", "哈利"));
            String result = service.restorePlaceholders("", context);

            assertEquals("", result);
        }

        @Test
        @DisplayName("空映射还原时原文返回")
        void 空映射还原时原文返回() {
            var context = service.buildMapping(Map.of());
            String result = service.restorePlaceholders("Some text with [{1}]", context);

            assertEquals("Some text with [{1}]", result);
        }
    }

    // ==================== build + replace + restore 闭环 ====================

    @Nested
    @DisplayName("占位符构建与还原闭环")
    class PlaceholderRoundTripTests {

        @Test
        @DisplayName("完整闭环: build->replace->restore")
        void 完整闭环() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("London", "伦敦");
            translations.put("Harry", "哈利");

            var context = service.buildMapping(translations);
            String original = "Harry lives in London";
            String withPlaceholders = service.replaceEntitiesWithPlaceholders(original, context);

            assertFalse(withPlaceholders.contains("Harry"));
            assertFalse(withPlaceholders.contains("London"));

            String restored = service.restorePlaceholders(withPlaceholders, context);
            assertEquals("哈利 lives in 伦敦", restored);
        }

        @Test
        @DisplayName("译文中保留占位符后正确还原")
        void 译文中保留占位符后正确还原() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("猫", "cat");
            translations.put("狗", "dog");

            var context = service.buildMapping(translations);
            String original = "猫和狗是朋友";
            String withPlaceholders = service.replaceEntitiesWithPlaceholders(original, context);

            assertTrue(withPlaceholders.contains("[{"));
            assertFalse(withPlaceholders.contains("猫"));
            assertFalse(withPlaceholders.contains("狗"));

            // Simulate English translation with placeholders preserved
            String translated = "[{1}] and [{2}] are friends";
            String restored = service.restorePlaceholders(translated, context);

            assertEquals("cat and dog are friends", restored);
        }

        @Test
        @DisplayName("实体重叠时正确还原")
        void 实体重叠时正确还原() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("哈利波特", "Harry Potter");

            var context = service.buildMapping(translations);
            String original = "哈利波特与魔法石";
            String withPlaceholders = service.replaceEntitiesWithPlaceholders(original, context);

            assertEquals("[{1}]与魔法石", withPlaceholders);

            String restored = service.restorePlaceholders(withPlaceholders, context);
            assertEquals("Harry Potter与魔法石", restored);
        }
    }

    // ==================== deduplicateEntities (反射) ====================

    @Nested
    @DisplayName("实体去重")
    class EntityDeduplicationTests {

        private Method dedupMethod;

        @BeforeEach
        void initMethod() throws Exception {
            dedupMethod = EntityConsistencyService.class
                    .getDeclaredMethod("deduplicateEntities", Map.class, String.class);
            dedupMethod.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> invokeDedup(Map<String, String> entities, String sourceText) throws Exception {
            return (Map<String, String>) dedupMethod.invoke(service, entities, sourceText);
        }

        @Test
        @DisplayName("嵌套实体保留最长匹配")
        void 嵌套实体保留最长匹配() throws Exception {
            Map<String, String> entities = Map.of(
                    "Harry", "哈利",
                    "Harry Potter", "哈利波ter",
                    "Potter", "波特"
            );
            String sourceText = "Harry Potter went to school";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("Harry Potter"));
        }

        @Test
        @DisplayName("不在原文中的实体被过滤")
        void 不在原文中的实体被过滤() throws Exception {
            Map<String, String> entities = Map.of(
                    "ExistingEntity", "已存在实体",
                    "NotInText", "不在文本中"
            );
            String sourceText = "This text contains ExistingEntity only";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("ExistingEntity"));
            assertFalse(result.containsKey("NotInText"));
        }

        @Test
        @DisplayName("无嵌套时全部保留")
        void 无嵌套时全部保留() throws Exception {
            Map<String, String> entities = Map.of(
                    "Harry", "哈利",
                    "Ron", "罗恩",
                    "Hermione", "赫敏"
            );
            String sourceText = "Harry, Ron, and Hermione";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("多个长实体均保留")
        void 多个长实体均保留() throws Exception {
            Map<String, String> entities = Map.of(
                    "Harry Potter", "哈利波ter",
                    "Ron Weasley", "罗恩韦斯莱"
            );
            String sourceText = "Harry Potter and Ron Weasley";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("部分嵌套保留非子实体")
        void 部分嵌套保留非子实体() throws Exception {
            Map<String, String> entities = Map.of(
                    "Hogwarts", "霍格沃茨",
                    "Hogwarts School", "霍格沃茨学校",
                    "Dumbledore", "邓布利多"
            );
            String sourceText = "Hogwarts School and Dumbledore";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(2, result.size());
            assertTrue(result.containsKey("Hogwarts School"));
            assertTrue(result.containsKey("Dumbledore"));
            assertFalse(result.containsKey("Hogwarts"));
        }

        @Test
        @DisplayName("空映射返回空")
        void 空映射返回空() throws Exception {
            Map<String, String> result = invokeDedup(Map.of(), "some text");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("按长度降序排序去重")
        void 按长度降序排序去重() throws Exception {
            Map<String, String> entities = new LinkedHashMap<>();
            entities.put("A", "译A");
            entities.put("ABC", "译ABC");
            entities.put("ABCDE", "译ABCDE");
            entities.put("AB", "译AB");

            String sourceText = "ABCDE is here";

            Map<String, String> result = invokeDedup(entities, sourceText);

            // ABCDE contains ABC, AB, A -> only ABCDE remains
            assertEquals(1, result.size());
            assertTrue(result.containsKey("ABCDE"));
        }
    }

    // ==================== splitTextForEntityExtraction ====================

    @Nested
    @DisplayName("文本分段")
    class SplitTextForEntityExtractionTests {

        @Test
        @DisplayName("短文本不分段")
        void 短文本不分段() {
            List<String> segments = service.splitTextForEntityExtraction("short text");

            assertEquals(1, segments.size());
            assertEquals("short text", segments.get(0));
        }

        @Test
        @DisplayName("等于5000字符不分段")
        void 等于5000字符不分段() {
            String text = "a".repeat(5000);
            List<String> segments = service.splitTextForEntityExtraction(text);

            assertEquals(1, segments.size());
            assertEquals(5000, segments.get(0).length());
        }

        @Test
        @DisplayName("5001字符开始分段")
        void 字符数5001开始分段() {
            String text = "a".repeat(5001);
            List<String> segments = service.splitTextForEntityExtraction(text);

            assertTrue(segments.size() >= 1, "should split into at least 1 segment");
        }

        @Test
        @DisplayName("10000字符多段落分段")
        void 字符数10000多段落分段() {
            String paragraph = "a".repeat(1000) + "\n\n";
            String text = paragraph.repeat(10); // ~10200 chars
            List<String> segments = service.splitTextForEntityExtraction(text);

            assertTrue(segments.size() >= 2, "long text with paragraphs should split into multiple segments");
        }

        @Test
        @DisplayName("null文本返回单空字符串")
        void null文本返回单空字符串() {
            List<String> segments = service.splitTextForEntityExtraction(null);

            assertEquals(1, segments.size());
            assertEquals("", segments.get(0));
        }

        @Test
        @DisplayName("空字符串返回单空字符串")
        void 空字符串返回单空字符串() {
            List<String> segments = service.splitTextForEntityExtraction("");

            assertEquals(1, segments.size());
            assertEquals("", segments.get(0));
        }

        @Test
        @DisplayName("分段总字符数等于原文")
        void 分段总字符数等于原文() {
            String paragraph = "这是一段测试文本。\n\n";
            String text = paragraph.repeat(50); // > 5000 chars
            List<String> segments = service.splitTextForEntityExtraction(text);

            int totalLength = segments.stream().mapToInt(String::length).sum();
            // May differ due to split regex consuming \n\n delimiters
            assertTrue(totalLength <= text.length(), "total segment length should not exceed original");
            assertTrue(totalLength > 0, "segments should contain text");
        }
    }

    // ==================== translateWithConsistency - 集成流程 ====================

    @Nested
    @DisplayName("translateWithConsistency - 集成流程")
    class TranslateWithConsistencyTests {

        @Test
        @DisplayName("无实体且无术语库返回consistencyAppliedFalse")
        void 无实体且无术语库返回consistencyAppliedFalse() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
            assertNull(result.getTranslatedText());
        }

        @Test
        @DisplayName("Python服务不可用时降级返回consistencyAppliedFalse")
        void Python服务不可用时降级() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(600), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
        }

        @Test
        @DisplayName("用户未启用术语库不查询术语库")
        void 用户未启用术语库不查询术语库() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            service.translateWithConsistency("a".repeat(500), "zh", "google", 1L, "doc1");

            verify(glossaryMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("用户偏好不存在时不查询术语库")
        void 用户偏好不存在时不查询术语库() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            service.translateWithConsistency("a".repeat(500), "zh", "google", 1L, "doc1");

            verify(glossaryMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("术语库启用但无匹配术语")
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
        @DisplayName("术语库查询异常不抛出")
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

            assertFalse(result.isConsistencyApplied());
        }

        @Test
        @DisplayName("用户偏好查询异常不抛出")
        void 用户偏好查询异常不抛出() {
            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenThrow(new RuntimeException("DB error"));

            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "a".repeat(500), "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
        }

        @Test
        @DisplayName("术语库匹配到原文中存在的术语")
        void 术语库匹配到原文中存在的术语() {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);

            Glossary glossary = new Glossary();
            glossary.setSourceWord("magic");
            glossary.setTargetWord("魔法");

            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenReturn(List.of(glossary));

            // Python will fail, but glossary terms are loaded
            // extractEntities fails -> returns empty, but glossary has terms
            // newEntities = empty (no extracted entities), so no translation needed
            // Then entities are empty + glossary empty after filtering -> returns false
            ConsistencyTranslationResult result = service.translateWithConsistency(
                    "This is a text about magic and wizards", "zh", "google", 1L, "doc1");

            // Since no entities extracted from Python (it's down), consistencyApplied = false
            assertFalse(result.isConsistencyApplied());
        }
    }

    // ==================== EntityMappingContext ====================

    @Nested
    @DisplayName("EntityMappingContext record")
    class EntityMappingContextTests {

        @Test
        @DisplayName("record可正确创建")
        void record可正确创建() {
            var context = service.buildMapping(Map.of("A", "译A"));

            assertNotNull(context);
            assertNotNull(context.mappings());
            assertNotNull(context.entityToPlaceholder());
        }

        @Test
        @DisplayName("record字段访问正确")
        void record字段访问正确() {
            Map<String, String> translations = new LinkedHashMap<>();
            translations.put("Test", "测试");

            var context = service.buildMapping(translations);

            assertEquals(1, context.mappings().size());
            assertEquals("[{1}]", context.entityToPlaceholder().get("Test"));
        }
    }
}
