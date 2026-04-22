package com.yumu.noveltranslator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranslationPostProcessingServiceTest {

    private TranslationPostProcessingService service;

    @BeforeEach
    void setUp() {
        service = new TranslationPostProcessingService();
        ReflectionTestUtils.setField(service, "pythonTranslateUrl",
                "http://localhost:8000/translate");
    }

    @Nested
    @DisplayName("修复未翻译中文")
    class FixUntranslatedChineseTests {

        @Test
        void 无残留中文返回原文() {
            String result = service.fixUntranslatedChinese(
                    "Hello world", "This is a clean translation", "en", "google");

            assertEquals("This is a clean translation", result);
        }

        @Test
        void 有中文但补救失败返回原文() {
            // Use an unreachable URL to guarantee remediation failure
            ReflectionTestUtils.setField(service, "pythonTranslateUrl",
                    "http://nonexistent.invalid.host:99999/translate");

            String translated = "The cat is 一只猫 and sat on the mat";
            String result = service.fixUntranslatedChinese(
                    "The cat is a cat", translated, "en", "google");

            // Remediation fails due to unreachable host, returns original
            assertEquals(translated, result);
        }
    }

    @Nested
    @DisplayName("检测中文段落")
    class DetectChineseSegmentsTests {

        @Test
        void 纯英文无中文返回空列表() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("detectChineseSegments", String.class);
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<String> segments = (List<String>) method.invoke(service, "This is pure English text");

            assertNotNull(segments);
            assertTrue(segments.isEmpty());
        }

        @Test
        void 混合中英文检测到中文段() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("detectChineseSegments", String.class);
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<String> segments = (List<String>) method.invoke(
                    service, "The cat is 一只猫 and sat on the 垫子上");

            assertNotNull(segments);
            assertEquals(2, segments.size());
            assertTrue(segments.contains("一只猫"));
            assertTrue(segments.contains("垫子上"));
        }

        @Test
        void 重复中文段去重() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("detectChineseSegments", String.class);
            method.setAccessible(true);

            // Use non-adjacent segments to test deduplication
            @SuppressWarnings("unchecked")
            List<String> segments = (List<String>) method.invoke(
                    service, "Hello 中文 world 中文 end 中文 more text");

            assertNotNull(segments);
            // "中文" appears 3 times but should be deduplicated to one entry
            assertEquals(1, segments.size());
            assertEquals("中文", segments.get(0));
        }

        @Test
        void 单个中文字符不匹配() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("detectChineseSegments", String.class);
            method.setAccessible(true);

            // The pattern requires 2+ consecutive Chinese characters
            @SuppressWarnings("unchecked")
            List<String> segments = (List<String>) method.invoke(
                    service, "A single 中 character X 文 test");

            assertNotNull(segments);
            assertTrue(segments.isEmpty());
        }
    }

    @Nested
    @DisplayName("应用补救")
    class ApplyRemediationTests {

        @Test
        void 简单段落替换() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("applyRemediation", String.class, List.class, String.class);
            method.setAccessible(true);

            String translated = "The cat is 一只猫";
            List<String> segments = List.of("一只猫");
            String remedied = "a cat";

            @SuppressWarnings("unchecked")
            String result = (String) method.invoke(service, translated, segments, remedied);

            assertEquals("The cat is a cat", result);
        }

        @Test
        void 多个段落替换() throws Exception {
            Method method = TranslationPostProcessingService.class
                    .getDeclaredMethod("applyRemediation", String.class, List.class, String.class);
            method.setAccessible(true);

            String translated = "The 猫 sat on the 垫子";
            List<String> segments = List.of("猫", "垫子");
            String remedied = "cat\nmat";

            @SuppressWarnings("unchecked")
            String result = (String) method.invoke(service, translated, segments, remedied);

            assertEquals("The cat sat on the mat", result);
        }
    }
}
