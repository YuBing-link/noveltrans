package com.yumu.noveltranslator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExternalResponseUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nullInputReturnsNull() {
        assertNull(ExternalResponseUtil.extractDataField(null));
    }

    @Test
    void extractsTranslatedContent() {
        String json = "{\"translatedContent\":\"Hello World\",\"success\":true,\"engine\":\"google\"}";
        assertEquals("Hello World", ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void extractsDataField() {
        String json = "{\"code\":200,\"data\":\"some translated text\"}";
        assertEquals("some translated text", ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void returnsNullOnErrorCode() {
        String json = "{\"code\":500,\"data\":\"error\"}";
        assertNull(ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void returnsJsonWhenNoCodeOrData() {
        String json = "{\"engine\":\"google\",\"success\":true}";
        // 没有 code/data/translatedContent，应返回原始 JSON
        assertEquals(json, ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void returnsNullOnInvalidJson() {
        assertEquals(null, ExternalResponseUtil.extractDataField("not json"));
    }

    @Test
    void validateStatusCodeWithValidCode() throws Exception {
        String json = "{\"code\":200}";
        var result = ExternalResponseUtil.validateStatusCode(MAPPER.readTree(json));
        assertTrue(result.isValid());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void validateStatusCodeWithErrorCode() throws Exception {
        String json = "{\"code\":500}";
        var result = ExternalResponseUtil.validateStatusCode(MAPPER.readTree(json));
        assertFalse(result.isValid());
        assertEquals(500, result.getStatusCode());
    }

    @Test
    void buildErrorResponse() {
        String json = ExternalResponseUtil.buildErrorResponse("Something went wrong", "ERR_001", null);
        assertNotNull(json);
        assertTrue(json.contains("Something went wrong"));
        assertTrue(json.contains("ERR_001"));
        assertTrue(json.contains("false"));
    }

    @Test
    void buildErrorResponseWithDetails() {
        Map<String, Object> details = Map.of("retryAfter", Integer.valueOf(30));
        String json = ExternalResponseUtil.buildErrorResponse("Rate limited", "RATE_LIMIT", details);
        assertNotNull(json);
        assertTrue(json.contains("Rate limited"));
        assertTrue(json.contains("retryAfter"));
    }
}
