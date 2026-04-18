package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private UserLevelThrottledTranslationClient translationClient;

    @Mock
    private TranslationCacheService cacheService;

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(translationClient, cacheService);
    }

    @Test
    void selectionTranslateReturnsCachedResult() {
        when(cacheService.getCache(anyString())).thenReturn("cached translation");

        SelectionTranslationRequest req = new SelectionTranslationRequest();
        req.setContext("Hello World");
        req.setSourceLang("en");
        req.setTargetLang("zh");
        req.setEngine("google");
        SelectionTranslateResponse resp = translationService.selectionTranslate(req);

        assertTrue(resp.getSuccess());
        assertEquals("cached translation", resp.getTranslation());
    }

    @Test
    void selectionTranslateReturnsErrorForEmptyText() {
        SelectionTranslationRequest req = new SelectionTranslationRequest();
        req.setContext("");
        SelectionTranslateResponse resp = translationService.selectionTranslate(req);

        assertFalse(resp.getSuccess());
        assertEquals("内容为空", resp.getTranslation());
    }

    @Test
    void selectionTranslateReturnsErrorForNullText() {
        SelectionTranslationRequest req = new SelectionTranslationRequest();
        req.setContext(null);
        SelectionTranslateResponse resp = translationService.selectionTranslate(req);

        assertFalse(resp.getSuccess());
    }

    @Test
    void selectionTranslateFallsBackOnError() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"code\":200,\"data\":\"translated via fallback\"}");

        SelectionTranslationRequest req = new SelectionTranslationRequest();
        req.setContext("Hello World");
        SelectionTranslateResponse resp = translationService.selectionTranslate(req);

        assertTrue(resp.getSuccess());
        assertEquals("translated via fallback", resp.getTranslation());
    }

    @Test
    void selectionTranslateDefaultEngineAndTarget() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"code\":200,\"data\":\"result\"}");

        SelectionTranslationRequest req = new SelectionTranslationRequest();
        req.setContext("test");
        SelectionTranslateResponse resp = translationService.selectionTranslate(req);

        assertTrue(resp.getSuccess());
        verify(translationClient).translate(eq("test"), eq("zh"), eq("auto"), eq(false));
    }

    @Test
    void readerTranslateReturnsCachedResult() {
        when(cacheService.getCache(anyString())).thenReturn("cached");

        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent("<p>Hello</p>");
        req.setSourceLang("en");
        req.setTargetLang("zh");
        req.setEngine("google");
        ReaderTranslateResponse resp = translationService.readerTranslate(req);

        assertTrue(resp.getSuccess());
        assertEquals("cached", resp.getTranslatedContent());
    }

    @Test
    void readerTranslateReturnsErrorForEmptyContent() {
        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent("");
        ReaderTranslateResponse resp = translationService.readerTranslate(req);

        assertFalse(resp.getSuccess());
        assertEquals("没有收到内容", resp.getTranslatedContent());
    }

    @Test
    void readerTranslateWithNullContent() {
        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent(null);
        ReaderTranslateResponse resp = translationService.readerTranslate(req);

        assertFalse(resp.getSuccess());
    }

    @Test
    void readerTranslateUsesMTranForHtmlMode() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"success\":true,\"engine\":\"mtran\",\"translatedContent\":\"翻译结果\"}");

        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent("<p>Test paragraph</p>");
        ReaderTranslateResponse resp = translationService.readerTranslate(req);

        assertTrue(resp.getSuccess());
        verify(translationClient).translate(anyString(), eq("zh"), eq("auto"), eq(true));
    }

    @Test
    void getCacheStatsDelegatesToCacheService() {
        Map<String, Object> expected = Map.of("l1Hits", 5L, "misses", 2L);
        when(cacheService.getCacheStats()).thenReturn(expected);

        Map<String, Object> stats = translationService.getCacheStats();
        assertEquals(expected, stats);
    }

    @Test
    void readerTranslateLongTextSegmentsCorrectly() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String text = invocation.getArgument(0);
                    return "{\"code\":200,\"data\":\"[translated:\" + text + \"]\"}";
                });

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append("This is sentence ").append(i).append(". ");
        }

        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent(sb.toString());
        req.setSourceLang("en");
        req.setTargetLang("zh");
        ReaderTranslateResponse resp = translationService.readerTranslate(req);

        assertTrue(resp.getSuccess());
        assertNotNull(resp.getTranslatedContent());
    }
}
