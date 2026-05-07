package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.dto.translation.ReaderTranslateResponse;
import com.yumu.noveltranslator.dto.translation.SelectionTranslateResponse;
import com.yumu.noveltranslator.dto.translation.ReaderTranslateRequest;
import com.yumu.noveltranslator.dto.translation.SelectionTranslationRequest;
import com.yumu.noveltranslator.dto.translation.WebpageTranslateRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface TranslatePort {
    SelectionTranslateResponse selectionTranslate(SelectionTranslationRequest req);
    ReaderTranslateResponse readerTranslate(ReaderTranslateRequest req);
    SseEmitter webpageTranslateStream(WebpageTranslateRequest req);
    SseEmitter streamTextTranslate(SelectionTranslationRequest req);
    Map<String, Object> getCacheStats();
}
