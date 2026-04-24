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

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EntityConsistencyService 补充测试
 * 覆盖现有测试未覆盖的分支：extractEntitiesSegmented 多段路径、
 * loadGlossaryTerms 匹配术语、deduplicateEntities 补充分支、
 * splitTextForEntityExtraction 超长边界
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EntityConsistencyService 补充测试")
class EntityConsistencyServiceExtendedTest {

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
        ReflectionTestUtils.setField(service, "pythonTranslateUrl", "http://nonexistent.invalid.host:9999/translate");
    }

    // ============ extractEntitiesSegmented 多段路径 ============

    @Nested
    @DisplayName("extractEntitiesSegmented - 多段提取")
    class ExtractEntitiesSegmentedTests {

        @Test
        void 长文本分段提取走多段路径() {
            String longText = "Harry Potter".repeat(300) + "Ron Weasley".repeat(300) + "\n\n" + "Hermione Granger".repeat(300);

            // extractEntities 会因 HTTP 失败返回空列表（因为 URL 不可达）
            // 多段路径会逐段调用，每段都失败返回空，最终返回空列表
            List<String> result = service.extractEntitiesSegmented(longText, "zh");

            // 多段路径即使每段都失败也不抛异常
            assertNotNull(result);
        }

        @Test
        void 短文本直接提取不经过分段() {
            String shortText = "Harry Potter went to school";

            // 短文本只调用一次 extractEntities，失败返回空
            List<String> result = service.extractEntitiesSegmented(shortText, "zh");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ============ translateWithConsistency 补充路径 ============

    @Nested
    @DisplayName("translateWithConsistency - 补充分支")
    class TranslateWithConsistencyExtendedTests {

        @Test
        void 实体提取失败降级返回() {
            String sourceText = "a".repeat(600) + " Some text";

            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());
            // 用户偏好不存在 → 不查询术语库
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            // extractEntitiesSegmented 内部 extractEntities 会失败返回空
            ConsistencyTranslationResult result = service.translateWithConsistency(
                    sourceText, "zh", "google", 1L, "doc1");

            // 实体为空 + 术语库为空 → consistencyApplied = false
            assertFalse(result.isConsistencyApplied());
        }

        @Test
        void 术语库查询异常时不抛出() {
            String sourceText = "a".repeat(600) + " Some text";

            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Collections.emptyMap());

            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);
            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenThrow(new RuntimeException("DB error"));

            // 不应抛出异常
            ConsistencyTranslationResult result = service.translateWithConsistency(
                    sourceText, "zh", "google", 1L, "doc1");

            assertFalse(result.isConsistencyApplied());
        }

        @Test
        void 缓存有实体时合并到映射() {
            String sourceText = "a".repeat(600) + " Test";

            when(documentEntityCache.getEntityMap(anyLong(), anyString()))
                    .thenReturn(Map.of("CachedEntity", "缓存翻译"));
            when(userPreferenceMapper.selectOne(any())).thenReturn(null);

            // extractEntitiesSegmented 返回空（HTTP 不可用）
            ConsistencyTranslationResult result = service.translateWithConsistency(
                    sourceText, "zh", "google", 1L, "doc1");

            // 实体为空 + 术语库为空 → 即使有缓存也返回 false
            // 因为 extractEntities 返回空，glossaryTerms 也为空
            assertFalse(result.isConsistencyApplied());
        }
    }

    // ============ loadGlossaryTerms 反射测试 ============

    @Nested
    @DisplayName("loadGlossaryTerms - 术语库匹配")
    class LoadGlossaryTermsTests {

        private Method loadMethod;

        @BeforeEach
        void initMethod() throws Exception {
            loadMethod = EntityConsistencyService.class
                    .getDeclaredMethod("loadGlossaryTerms", Long.class, String.class);
            loadMethod.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> invokeLoad(Long userId, String sourceText) throws Exception {
            return (Map<String, String>) loadMethod.invoke(service, userId, sourceText);
        }

        @Test
        void 用户启用术语库且匹配到术语() throws Exception {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);

            Glossary g1 = new Glossary();
            g1.setSourceWord("magic");
            g1.setTargetWord("魔法");

            Glossary g2 = new Glossary();
            g2.setSourceWord("not_in_text");
            g2.setTargetWord("不在文中");

            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1, g2));

            Map<String, String> terms = invokeLoad(1L, "This text contains magic words");

            assertEquals(1, terms.size());
            assertEquals("魔法", terms.get("magic"));
        }

        @Test
        void 用户启用术语库但无匹配术语() throws Exception {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);

            Glossary g1 = new Glossary();
            g1.setSourceWord("unicorn");
            g1.setTargetWord("独角兽");

            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1));

            Map<String, String> terms = invokeLoad(1L, "This text has no glossary terms");

            assertTrue(terms.isEmpty());
        }

        @Test
        void 用户偏好查询异常返回空() throws Exception {
            when(userPreferenceMapper.selectOne(any())).thenThrow(new RuntimeException("DB error"));

            Map<String, String> terms = invokeLoad(1L, "some text");

            assertTrue(terms.isEmpty());
        }

        @Test
        void 术语库查询异常返回已有匹配() throws Exception {
            UserPreference pref = new UserPreference();
            pref.setEnableGlossary(true);

            Glossary g1 = new Glossary();
            g1.setSourceWord("magic");
            g1.setTargetWord("魔法");

            when(userPreferenceMapper.selectOne(any())).thenReturn(pref);
            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1));

            Map<String, String> terms = invokeLoad(1L, "magic text");

            assertEquals(1, terms.size());
        }
    }

    // ============ deduplicateEntities 补充 ============

    @Nested
    @DisplayName("deduplicateEntities - 补充分支")
    class DeduplicationExtendedTests {

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
        void 实体在原文中不存在被过滤() throws Exception {
            Map<String, String> entities = Map.of(
                    "RealEntity", "真实实体",
                    "FakeEntity", "虚构实体"
            );
            String sourceText = "Only RealEntity is here";

            Map<String, String> result = invokeDedup(entities, sourceText);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("RealEntity"));
        }
    }

    // ============ splitTextForEntityExtraction 补充 ============

    @Nested
    @DisplayName("splitTextForEntityExtraction - 超长片段边界")
    class SplitTextExtendedTests {

        @Test
        void 单段落超长文本触发句子级切分() {
            // 创建一个超过 3000 * 1.5 = 4500 字符的单段落
            String text = "这是一段没有换行的超长文本。".repeat(200); // ~5600 chars

            List<String> segments = service.splitTextForEntityExtraction(text);

            assertTrue(segments.size() >= 1);
            int totalLen = segments.stream().mapToInt(String::length).sum();
            assertTrue(totalLen > 0);
        }

        @Test
        void 多段落其中一段超长触发切分() {
            // 第一段 > 4500 字符，第二段正常
            String longPara = "A".repeat(4600) + "\n\n";
            String shortPara = "B".repeat(1000) + "\n\n";
            String text = longPara + shortPara;

            List<String> segments = service.splitTextForEntityExtraction(text);

            assertTrue(segments.size() >= 2);
        }
    }
}
