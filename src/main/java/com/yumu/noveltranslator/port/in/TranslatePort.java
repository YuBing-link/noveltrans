package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.translation.ReaderTranslateResponse;
import com.yumu.noveltranslator.port.dto.translation.SelectionTranslateResponse;
import com.yumu.noveltranslator.port.dto.translation.ReaderTranslateRequest;
import com.yumu.noveltranslator.port.dto.translation.SelectionTranslationRequest;
import com.yumu.noveltranslator.port.dto.translation.WebpageTranslateRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface TranslatePort {
    SelectionTranslateResponse selectionTranslate(Long userId, SelectionTranslationRequest req);
    ReaderTranslateResponse readerTranslate(Long userId, ReaderTranslateRequest req);
    SseEmitter webpageTranslateStream(Long userId, WebpageTranslateRequest req);
    SseEmitter streamTextTranslate(Long userId, SelectionTranslationRequest req);
    Map<String, Object> getCacheStats();
}
