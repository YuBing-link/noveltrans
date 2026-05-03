package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextCleaningUtil 扩展测试
 * 补充已有 TextCleaningUtilTest 未覆盖的方法测试
 */
class TextCleaningUtilExtendedTest {

    // Fullwidth character constants using decimal code points (100% ASCII-safe)
    static final String FW_SPACE = String.valueOf((char) 12288);  // U+3000
    static final String FW_A = String.valueOf((char) 65313);       // U+FF21
    static final String FW_B = String.valueOf((char) 65314);       // U+FF22
    static final String FW_C = String.valueOf((char) 65315);       // U+FF23
    static final String FW_1 = String.valueOf((char) 65297);       // U+FF11
    static final String FW_2 = String.valueOf((char) 65298);       // U+FF12
    static final String FW_3 = String.valueOf((char) 65299);       // U+FF13
    static final String FW_BANG = String.valueOf((char) 65281);    // U+FF01

    @Nested
    @DisplayName("全角转半角")
    class FullWidthToHalfWidthTests {

        @Test
        @DisplayName("null 输入返回 null")
        void fullWidthToHalfWidth_null_returnsNull() {
            assertNull(TextCleaningUtil.fullWidthToHalfWidth(null));
        }

        @Test
        @DisplayName("全角空格转半角空格")
        void fullWidthToHalfWidth_fullwidthSpace() {
            assertEquals("a b", TextCleaningUtil.fullWidthToHalfWidth("a" + FW_SPACE + "b"));
        }

        @Test
        @DisplayName("全角字母数字和标点转半角")
        void fullWidthToHalfWidth_fullwidthChars() {
            String input = FW_A + FW_B + FW_C + FW_1 + FW_2 + FW_3 + FW_BANG;
            String result = TextCleaningUtil.fullWidthToHalfWidth(input);
            assertEquals("ABC123!", result);
        }

        @Test
        @DisplayName("半角字符保持不变")
        void fullWidthToHalfWidth_halfwidthUnchanged() {
            assertEquals("ABC123!", TextCleaningUtil.fullWidthToHalfWidth("ABC123!"));
        }

        @Test
        @DisplayName("混合文本正常转换")
        void fullWidthToHalfWidth_mixed() {
            assertEquals("Hello123!", TextCleaningUtil.fullWidthToHalfWidth("Hello" + FW_1 + FW_2 + FW_3 + FW_BANG));
        }
    }

    @Nested
    @DisplayName("半角转全角")
    class HalfWidthToFullWidthTests {

        @Test
        @DisplayName("null 输入返回 null")
        void halfWidthToFullWidth_null_returnsNull() {
            assertNull(TextCleaningUtil.halfWidthToFullWidth(null));
        }

        @Test
        @DisplayName("半角空格转全角空格")
        void halfWidthToFullWidth_space() {
            String result = TextCleaningUtil.halfWidthToFullWidth("a b");
            assertEquals(3, result.length());
            assertEquals(0x3000, (int) result.charAt(1));
        }

        @Test
        @DisplayName("半角字母数字和标点转全角")
        void halfWidthToFullWidth_halfwidthChars() {
            String expected = FW_A + FW_B + FW_C + FW_1 + FW_2 + FW_3 + FW_BANG;
            String result = TextCleaningUtil.halfWidthToFullWidth("ABC123!");
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("全角字符保持不变")
        void halfWidthToFullWidth_fullwidthUnchanged() {
            String input = FW_A + FW_B + FW_C;
            assertEquals(input, TextCleaningUtil.halfWidthToFullWidth(input));
        }

        @Test
        @DisplayName("混合文本正常转换")
        void halfWidthToFullWidth_mixed() {
            String result = TextCleaningUtil.halfWidthToFullWidth("Hello 123!");
            assertEquals(10, result.length());
            assertEquals(0x3000, (int) result.charAt(5));
            assertEquals(0xFF11, (int) result.charAt(6));
        }

        @Test
        @DisplayName("全半角转换互逆")
        void halfWidthToFullWidth_roundtrip() {
            String original = "Test123!@#";
            String full = TextCleaningUtil.halfWidthToFullWidth(original);
            String back = TextCleaningUtil.fullWidthToHalfWidth(full);
            assertEquals(original, back);
        }
    }

    @Nested
    @DisplayName("HTML 转义/反转义")
    class HtmlEscapeTests {

        @Test
        @DisplayName("null 输入返回 null")
        void escapeHtml_null_returnsNull() {
            assertNull(TextCleaningUtil.escapeHtml(null));
        }

        @Test
        @DisplayName("转义 HTML 特殊字符")
        void escapeHtml_specialChars() {
            String input = "<div class=\"test\">Hello & World</div>";
            String result = TextCleaningUtil.escapeHtml(input);
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
            assertTrue(result.contains("&quot;"));
        }

        @Test
        @DisplayName("反转义 HTML 实体")
        void unescapeHtml_entities() {
            String input = "&lt;div&gt;&amp;quot;&lt;/div&gt;";
            String result = TextCleaningUtil.unescapeHtml(input);
            assertEquals("<div>&quot;</div>", result);
        }

        @Test
        @DisplayName("反转义 null 返回 null")
        void unescapeHtml_null_returnsNull() {
            assertNull(TextCleaningUtil.unescapeHtml(null));
        }

        @Test
        @DisplayName("普通文本转义后不变")
        void escapeHtml_plainText() {
            assertEquals("Hello World", TextCleaningUtil.escapeHtml("Hello World"));
        }
    }

    @Nested
    @DisplayName("XML 转义/反转义")
    class XmlEscapeTests {

        @Test
        @DisplayName("null 输入返回 null")
        void escapeXml_null_returnsNull() {
            assertNull(TextCleaningUtil.escapeXml(null));
        }

        @Test
        @DisplayName("转义 XML 特殊字符")
        void escapeXml_specialChars() {
            String input = "<tag attr=\"value\">Text & more</tag>";
            String result = TextCleaningUtil.escapeXml(input);
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
            assertTrue(result.contains("&amp;"));
            assertTrue(result.contains("&quot;"));
        }

        @Test
        @DisplayName("反转义 XML 实体")
        void unescapeXml_entities() {
            String input = "&lt;tag&gt;&amp;&lt;/tag&gt;";
            String result = TextCleaningUtil.unescapeXml(input);
            assertEquals("<tag>&</tag>", result);
        }

        @Test
        @DisplayName("反转义 null 返回 null")
        void unescapeXml_null_returnsNull() {
            assertNull(TextCleaningUtil.unescapeXml(null));
        }
    }

    @Nested
    @DisplayName("CSV 转义/反转义")
    class CsvEscapeTests {

        @Test
        @DisplayName("null 输入返回 null")
        void escapeCsv_null_returnsNull() {
            assertNull(TextCleaningUtil.escapeCsv(null));
        }

        @Test
        @DisplayName("含逗号的文本加引号")
        void escapeCsv_withComma() {
            String result = TextCleaningUtil.escapeCsv("hello,world");
            assertEquals("\"hello,world\"", result);
        }

        @Test
        @DisplayName("含双引号的文本转义并加引号")
        void escapeCsv_withQuote() {
            String result = TextCleaningUtil.escapeCsv("say \"hello\"");
            assertEquals("\"say \"\"hello\"\"\"", result);
        }

        @Test
        @DisplayName("反转义 CSV")
        void unescapeCsv_quoted() {
            String result = TextCleaningUtil.unescapeCsv("\"hello,world\"");
            assertEquals("hello,world", result);
        }

        @Test
        @DisplayName("反转义 null 返回 null")
        void unescapeCsv_null_returnsNull() {
            assertNull(TextCleaningUtil.unescapeCsv(null));
        }
    }

    @Nested
    @DisplayName("Java 字符串转义/反转义")
    class JavaEscapeTests {

        @Test
        @DisplayName("null 输入返回 null")
        void escapeJava_null_returnsNull() {
            assertNull(TextCleaningUtil.escapeJava(null));
        }

        @Test
        @DisplayName("转义换行符和制表符")
        void escapeJava_specialChars() {
            String input = "line1\nline2\ttab";
            String result = TextCleaningUtil.escapeJava(input);
            assertEquals("line1\\nline2\\ttab", result);
        }

        @Test
        @DisplayName("转义反斜杠和双引号")
        void escapeJava_backslashAndQuote() {
            String input = "path\\to\\file and \"quote\"";
            String result = TextCleaningUtil.escapeJava(input);
            assertEquals("path\\\\to\\\\file and \\\"quote\\\"", result);
        }

        @Test
        @DisplayName("反转义 Java 字符串")
        void unescapeJava_escapes() {
            String input = "line1\\nline2\\ttab";
            String result = TextCleaningUtil.unescapeJava(input);
            assertEquals("line1\nline2\ttab", result);
        }

        @Test
        @DisplayName("反转义 null 返回 null")
        void unescapeJava_null_returnsNull() {
            assertNull(TextCleaningUtil.unescapeJava(null));
        }
    }

    @Nested
    @DisplayName("JSON 转义/反转义")
    class JsonEscapeTests {

        @Test
        @DisplayName("null 输入返回 null")
        void escapeJson_null_returnsNull() {
            assertNull(TextCleaningUtil.escapeJson(null));
        }

        @Test
        @DisplayName("转义 JSON 特殊字符")
        void escapeJson_specialChars() {
            String input = "line1\nline2\ttab";
            String result = TextCleaningUtil.escapeJson(input);
            assertEquals("line1\\nline2\\ttab", result);
        }

        @Test
        @DisplayName("反转义 JSON")
        void unescapeJson_escapes() {
            String input = "line1\\nline2\\ttab";
            String result = TextCleaningUtil.unescapeJson(input);
            assertEquals("line1\nline2\ttab", result);
        }

        @Test
        @DisplayName("反转义 null 返回 null")
        void unescapeJson_null_returnsNull() {
            assertNull(TextCleaningUtil.unescapeJson(null));
        }
    }

    @Nested
    @DisplayName("全角 trim")
    class TrimFullWidthTests {

        @Test
        @DisplayName("null 输入返回 null")
        void trimFullWidth_null_returnsNull() {
            assertNull(TextCleaningUtil.trimFullWidth(null));
        }

        @Test
        @DisplayName("去除全角空格")
        void trimFullWidth_fullwidthSpaces() {
            assertEquals("Hello", TextCleaningUtil.trimFullWidth(FW_SPACE + "Hello" + FW_SPACE));
        }

        @Test
        @DisplayName("去除混合空白字符")
        void trimFullWidth_mixedWhitespace() {
            assertEquals("Hello", TextCleaningUtil.trimFullWidth(FW_SPACE + " \tHello\t  " + FW_SPACE));
        }

        @Test
        @DisplayName("无前后空白时不变")
        void trimFullWidth_noTrim() {
            assertEquals("Hello", TextCleaningUtil.trimFullWidth("Hello"));
        }
    }

    @Nested
    @DisplayName("移除 BOM")
    class RemoveBomTests {

        @Test
        @DisplayName("null 输入返回 null")
        void removeBom_null_returnsNull() {
            assertNull(TextCleaningUtil.removeBom(null));
        }

        @Test
        @DisplayName("移除 BOM 标记")
        void removeBom_withBom() {
            assertEquals("Hello", TextCleaningUtil.removeBom(String.valueOf((char) 0xFEFF) + "Hello"));
        }

        @Test
        @DisplayName("无 BOM 时保持不变")
        void removeBom_noBom() {
            assertEquals("Hello", TextCleaningUtil.removeBom("Hello"));
        }
    }

    @Nested
    @DisplayName("规范化空白字符")
    class NormalizeAllWhitespaceTests {

        @Test
        @DisplayName("null 输入返回 null")
        void normalizeAllWhitespace_null_returnsNull() {
            assertNull(TextCleaningUtil.normalizeAllWhitespace(null));
        }

        @Test
        @DisplayName("多个空格压缩为单个")
        void normalizeAllWhitespace_multipleSpaces() {
            assertEquals("Hello World", TextCleaningUtil.normalizeAllWhitespace("Hello     World"));
        }

        @Test
        @DisplayName("制表符和换行符替换为空格")
        void normalizeAllWhitespace_tabsAndNewlines() {
            assertEquals("Hello World Test", TextCleaningUtil.normalizeAllWhitespace("Hello\tWorld\nTest"));
        }

        @Test
        @DisplayName("全角空格不压缩（Java \s 不匹配全角空格）")
        void normalizeAllWhitespace_fullwidthSpace() {
            String result = TextCleaningUtil.normalizeAllWhitespace("Hello" + FW_SPACE + "World");
            assertEquals("Hello" + FW_SPACE + "World", result);
        }
    }

    @Nested
    @DisplayName("清理空格")
    class CleanupSpacesTests {

        @Test
        @DisplayName("null 输入返回 null")
        void cleanupSpaces_null_returnsNull() {
            assertNull(TextCleaningUtil.cleanupSpaces(null));
        }

        @Test
        @DisplayName("压缩行内多余空格")
        void cleanupSpaces_compressInlineSpaces() {
            assertEquals("Hello World", TextCleaningUtil.cleanupSpaces("Hello   World"));
        }

        @Test
        @DisplayName("移除行首行尾空格保留换行")
        void cleanupSpaces_trimLines() {
            String input = "  line1  \n  line2  ";
            String result = TextCleaningUtil.cleanupSpaces(input);
            assertEquals("line1\nline2", result);
        }
    }

    @Nested
    @DisplayName("移除制表符")
    class RemoveTabsTests {

        @Test
        @DisplayName("null 输入返回 null")
        void removeTabs_null_returnsNull() {
            assertNull(TextCleaningUtil.removeTabs(null));
        }

        @Test
        @DisplayName("移除所有制表符")
        void removeTabs_removesAllTabs() {
            assertEquals("HelloWorldTest", TextCleaningUtil.removeTabs("Hello\tWorld\tTest"));
        }

        @Test
        @DisplayName("无制表符时保持不变")
        void removeTabs_noTabs() {
            assertEquals("Hello World", TextCleaningUtil.removeTabs("Hello World"));
        }
    }

    @Nested
    @DisplayName("HTML 净化 sanitizeHtml")
    class SanitizeHtmlTests {

        @Test
        @DisplayName("null 输入返回 null")
        void sanitizeHtml_null_returnsNull() {
            assertNull(TextCleaningUtil.sanitizeHtml(null));
        }

        @Test
        @DisplayName("空字符串返回空")
        void sanitizeHtml_empty_returnsEmpty() {
            assertEquals("", TextCleaningUtil.sanitizeHtml(""));
        }

        @Test
        @DisplayName("移除 script 标签")
        void sanitizeHtml_removesScript() {
            String input = "<p>Hello</p><script>alert('xss')</script>";
            String result = TextCleaningUtil.sanitizeHtml(input);
            assertFalse(result.contains("<script>"));
            assertTrue(result.contains("<p>Hello</p>"));
        }

        @Test
        @DisplayName("保留安全标签")
        void sanitizeHtml_keepsSafeTags() {
            String input = "<h1>Title</h1><p>Text</p><ul><li>Item</li></ul>";
            String result = TextCleaningUtil.sanitizeHtml(input);
            assertTrue(result.contains("<h1>Title</h1>"));
            assertTrue(result.contains("<p>Text</p>"));
            assertTrue(result.contains("<li>Item</li>"));
        }

        @Test
        @DisplayName("移除 javascript: 协议")
        void sanitizeHtml_removesJavascriptProtocol() {
            String input = "<a href=\"javascript:alert(1)\">link</a>";
            String result = TextCleaningUtil.sanitizeHtml(input);
            assertFalse(result.contains("javascript:"));
        }
    }

    @Nested
    @DisplayName("检测不可见字符")
    class HasInvisibleCharsTests {

        @Test
        @DisplayName("null 输入返回 false")
        void hasInvisibleChars_null_returnsFalse() {
            assertFalse(TextCleaningUtil.hasInvisibleChars(null));
        }

        @Test
        @DisplayName("空字符串返回 false")
        void hasInvisibleChars_empty_returnsFalse() {
            assertFalse(TextCleaningUtil.hasInvisibleChars(""));
        }

        @Test
        @DisplayName("含零宽字符返回 true")
        void hasInvisibleChars_zeroWidth_returnsTrue() {
            assertTrue(TextCleaningUtil.hasInvisibleChars("Hello" + (char) 0x200B + "World"));
        }

        @Test
        @DisplayName("含控制字符返回 true")
        void hasInvisibleChars_controlChar_returnsTrue() {
            assertTrue(TextCleaningUtil.hasInvisibleChars("Hello" + (char) 0x03 + "World"));
        }

        @Test
        @DisplayName("纯文本返回 false")
        void hasInvisibleChars_plainText_returnsFalse() {
            assertFalse(TextCleaningUtil.hasInvisibleChars("Hello World 123"));
        }

        @Test
        @DisplayName("含换行符返回 true（换行符匹配 \\p{Cntrl}）")
        void hasInvisibleChars_newline_returnsTrue() {
            assertTrue(TextCleaningUtil.hasInvisibleChars("Hello\nWorld"));
        }
    }
}
