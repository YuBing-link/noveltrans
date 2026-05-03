package com.yumu.noveltranslator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
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

    @Test
    void buildErrorResponseWithEnum() {
        String json = ExternalResponseUtil.buildErrorResponse(ErrorCodeEnum.PARAMETER_ERROR, null);
        assertNotNull(json);
        assertTrue(json.contains(ErrorCodeEnum.PARAMETER_ERROR.getCode()));
        assertTrue(json.contains(ErrorCodeEnum.PARAMETER_ERROR.getMessage()));
    }

    @Test
    void buildSimpleErrorResponseWithEnum() {
        String json = ExternalResponseUtil.buildSimpleErrorResponse(ErrorCodeEnum.UNAUTHORIZED);
        assertNotNull(json);
        assertTrue(json.contains(ErrorCodeEnum.UNAUTHORIZED.getCode()));
        assertTrue(json.contains("false"));
    }

    @Test
    void buildTranslatedPath_null_returnsNull() {
        assertNull(ExternalResponseUtil.buildTranslatedPath(null));
    }

    @Test
    void buildTranslatedPath_withExtension() {
        assertEquals("/docs/file_translated.txt",
            ExternalResponseUtil.buildTranslatedPath("/docs/file.txt"));
    }

    @Test
    void buildTranslatedPath_noExtension() {
        assertEquals("/docs/file_translated",
            ExternalResponseUtil.buildTranslatedPath("/docs/file"));
    }

    @Test
    void buildTranslatedPath_leadingDotOnly() {
        assertEquals(".hidden_translated",
            ExternalResponseUtil.buildTranslatedPath(".hidden"));
    }

    @Test
    void processingReader_parsesHtml() {
        String html = "<html><body><h1>Title</h1><p>Para</p></body></html>";
        java.util.List<String> result = ExternalResponseUtil.processingReader(html);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void processingReader_emptyString() {
        java.util.List<String> result = ExternalResponseUtil.processingReader("");
        assertNotNull(result);
    }

    @Test
    void extractDataField_dataIsObject_returnsToString() throws Exception {
        String json = "{\"code\":200,\"data\":{\"nested\":\"value\"}}";
        assertEquals("{\"nested\":\"value\"}", ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void extractDataField_translatedContentIsObject_returnsToString() {
        String json = "{\"translatedContent\":{\"a\":\"b\"}}";
        assertEquals("{\"a\":\"b\"}", ExternalResponseUtil.extractDataField(json));
    }

    @Test
    void extractDataField_rootIsPlainString_returnsString() {
        assertEquals("plain", ExternalResponseUtil.extractDataField("\"plain\""));
    }
}
