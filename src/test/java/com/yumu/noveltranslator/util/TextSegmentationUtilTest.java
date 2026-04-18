package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextSegmentationUtilTest {

    @Test
    void emptyTextReturnsEmptyList() {
        assertTrue(TextSegmentationUtil.segmentText("", 1000).isEmpty());
        assertTrue(TextSegmentationUtil.segmentText(null, 1000).isEmpty());
    }

    @Test
    void shortTextWithinLimit() {
        var result = TextSegmentationUtil.segmentText("Hello world", 100);
        assertEquals(1, result.size());
        assertEquals("Hello world", result.get(0));
    }

    @Test
    void longTextSplitAtSentenceBoundary() {
        String text = "This is the first sentence. This is the second sentence. And the third one.";
        var result = TextSegmentationUtil.segmentText(text, 30);
        assertTrue(result.size() >= 2);
        // 验证没有丢失内容
        String combined = String.join("", result);
        assertEquals(text, combined);
    }

    @Test
    void veryLongTextWithMultipleSegments() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Sentence ").append(i).append(". ");
        }
        var result = TextSegmentationUtil.segmentText(sb.toString(), 100);
        assertTrue(result.size() > 1);
        assertEquals(sb.toString().trim(), String.join("", result).trim());
    }

    @Test
    void segmentByEngineGoogle() {
        var result = TextSegmentationUtil.segmentByTextEngine("Hello world. Test text.", "google");
        assertEquals(1, result.size());
    }

    @Test
    void segmentByEngineMyMemoryShorterLimit() {
        // MyMemory 限制 450 字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Test ").append(i).append(". ");
        }
        var result = TextSegmentationUtil.segmentByTextEngine(sb.toString(), "mymemory");
        assertTrue(result.size() >= 1);
    }

    @Test
    void unknownEngineReturnsDefaultLimit() {
        var result = TextSegmentationUtil.segmentByTextEngine("Hello world", "unknown_engine");
        assertEquals(1, result.size());
        assertEquals("Hello world", result.get(0));
    }

    @Test
    void estimateSegmentsCount() {
        String text = "A".repeat(3000);
        int count = TextSegmentationUtil.estimateSegmentsCount(text, "google");
        assertTrue(count >= 3);
    }

    @Test
    void estimateSegmentsEmptyText() {
        assertEquals(0, TextSegmentationUtil.estimateSegmentsCount("", "google"));
        assertEquals(0, TextSegmentationUtil.estimateSegmentsCount(null, "google"));
    }
}
