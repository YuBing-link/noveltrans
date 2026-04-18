package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextCleaningUtilTest {

    @Test
    void cleanNullReturnsNull() {
        assertNull(TextCleaningUtil.cleanText(null));
    }

    @Test
    void cleanEmptyReturnsEmpty() {
        assertEquals("", TextCleaningUtil.cleanText(""));
    }

    @Test
    void cleanTextRemovesZeroWidthChars() {
        String text = "Hello\u200BWorld";
        String cleaned = TextCleaningUtil.cleanText(text);
        assertEquals("HelloWorld", cleaned);
    }

    @Test
    void cleanTextRemovesBOM() {
        String text = "\uFEFFHello World";
        String cleaned = TextCleaningUtil.cleanText(text);
        assertEquals("Hello World", cleaned);
    }

    @Test
    void cleanTextPreservesNormalSpaces() {
        String text = "Hello   World";
        String cleaned = TextCleaningUtil.cleanText(text);
        assertEquals("Hello   World", cleaned);
    }

    @Test
    void cleanTextPreservesNewlines() {
        String text = "Line1\nLine2\r\nLine3";
        String cleaned = TextCleaningUtil.cleanText(text);
        assertEquals("Line1\nLine2\r\nLine3", cleaned);
    }

    @Test
    void cleanTextPreservesHtmlTags() {
        String text = "<p>Hello</p>";
        String cleaned = TextCleaningUtil.cleanText(text);
        assertEquals("<p>Hello</p>", cleaned);
    }

    @Test
    void removeSpecialInvisibleChars() {
        assertNull(TextCleaningUtil.removeSpecialInvisibleChars(null));
    }

    @Test
    void removeControlChars() {
        assertNull(TextCleaningUtil.removeControlChars(null));
    }

    @Test
    void removeInvalidUnicode() {
        assertNull(TextCleaningUtil.removeInvalidUnicode(null));
    }

    @Test
    void trimFullWidth() {
        String text = "\u3000  Hello  \u3000";
        String trimmed = TextCleaningUtil.trimFullWidth(text);
        assertEquals("Hello", trimmed);
    }

    @Test
    void trimFullWidthNull() {
        assertNull(TextCleaningUtil.trimFullWidth(null));
    }

    @Test
    void removeBom() {
        assertEquals("Hello", TextCleaningUtil.removeBom("\uFEFFHello"));
        assertEquals("Hello", TextCleaningUtil.removeBom("Hello"));
        assertNull(TextCleaningUtil.removeBom(null));
    }

    @Test
    void normalizeAllWhitespace() {
        assertEquals("Hello World", TextCleaningUtil.normalizeAllWhitespace("Hello\t\n  World"));
        assertNull(TextCleaningUtil.normalizeAllWhitespace(null));
    }

    @Test
    void cleanupSpaces() {
        assertEquals("Hello World", TextCleaningUtil.cleanupSpaces("Hello   World"));
        assertNull(TextCleaningUtil.cleanupSpaces(null));
    }

    @Test
    void removeTabs() {
        assertEquals("HelloWorld", TextCleaningUtil.removeTabs("Hello\tWorld"));
        assertNull(TextCleaningUtil.removeTabs(null));
    }

    @Test
    void hasInvisibleChars() {
        assertTrue(TextCleaningUtil.hasInvisibleChars("Hello\u200BWorld"));
        assertFalse(TextCleaningUtil.hasInvisibleChars("Hello World"));
        assertFalse(TextCleaningUtil.hasInvisibleChars(""));
    }
}
