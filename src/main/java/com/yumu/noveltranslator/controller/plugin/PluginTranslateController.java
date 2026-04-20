package com.yumu.noveltranslator.controller.plugin;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 浏览器扩展翻译接口
 * 路径前缀: /v1/translate
 */
@RestController
@RequestMapping("/v1/translate")
@RequiredArgsConstructor
public class PluginTranslateController {

    private final TranslationService translationService;

    /**
     * 选中文本翻译 - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/selection
     */
    @PostMapping(value = "/selection")
    public SelectionTranslateResponse translateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        return translationService.selectionTranslate(req);
    }

    /**
     * 阅读器翻译 - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/reader
     */
    @PostMapping(value = "/reader")
    public ReaderTranslateResponse translateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        return translationService.readerTranslate(req);
    }

    /**
     * 网页翻译 - 允许公共访问，SSE 流式输出
     * POST /v1/translate/webpage
     */
    @PostMapping(value = "/webpage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter translateWebpage(@RequestBody @Valid WebpageTranslateRequest req) {
        return translationService.webpageTranslateStream(req);
    }

    /**
     * 文本流式翻译（SSE）— 适用于长文本的单段流式输出
     * POST /v1/translate/text/stream
     */
    @PostMapping(value = "/text/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTextTranslate(@RequestBody @Valid SelectionTranslationRequest req) {
        return translationService.streamTextTranslate(req);
    }

    /**
     * 为认证用户提供高级翻译功能
     * POST /v1/translate/premium-selection
     */
    @PostMapping(value = "/premium-selection")
    @PreAuthorize("isAuthenticated()")
    public SelectionTranslateResponse premiumTranslateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        return translationService.selectionTranslate(req);
    }

    /**
     * 为认证用户提供高级阅读器翻译
     * POST /v1/translate/premium-reader
     */
    @PostMapping(value = "/premium-reader")
    @PreAuthorize("isAuthenticated()")
    public ReaderTranslateResponse premiumTranslateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        return translationService.readerTranslate(req);
    }
}
