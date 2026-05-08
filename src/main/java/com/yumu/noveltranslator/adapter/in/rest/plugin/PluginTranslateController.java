package com.yumu.noveltranslator.adapter.in.rest.plugin;

import com.yumu.noveltranslator.port.dto.translation.SelectionTranslateResponse;
import com.yumu.noveltranslator.port.dto.translation.SelectionTranslationRequest;
import com.yumu.noveltranslator.port.dto.translation.ReaderTranslateResponse;
import com.yumu.noveltranslator.port.dto.translation.ReaderTranslateRequest;
import com.yumu.noveltranslator.port.dto.translation.WebpageTranslateRequest;
import com.yumu.noveltranslator.port.in.TranslatePort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PluginTranslateController {

    private final TranslatePort translatePort;

    /**
     * 选中文本翻译 - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/selection
     */
    @PostMapping(value = "/selection")
    public SelectionTranslateResponse translateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        return translatePort.selectionTranslate(userId, req);
    }

    /**
     * 阅读器翻译 - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/reader
     */
    @PostMapping(value = "/reader")
    public ReaderTranslateResponse translateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        return translatePort.readerTranslate(userId, req);
    }

    /**
     * 网页翻译 - 允许公共访问，SSE 流式输出
     * POST /v1/translate/webpage
     */
    @PostMapping(value = "/webpage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter translateWebpage(@RequestBody @Valid WebpageTranslateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        return translatePort.webpageTranslateStream(userId, req);
    }

    /**
     * 文本流式翻译（SSE）— 适用于长文本的单段流式输出
     * POST /v1/translate/text/stream
     */
    @PostMapping(value = "/text/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTextTranslate(@RequestBody @Valid SelectionTranslationRequest req) {
        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        log.info("[STREAM-TRACE] Controller entry: /v1/translate/text/stream, userId={}, engine={}, targetLang={}, mode={}, textLen={}",
                userId, req.getEngine(), req.getTargetLang(), req.getMode(), req.getText() != null ? req.getText().length() : 0);
        return translatePort.streamTextTranslate(userId, req);
    }

    /**
     * 为认证用户提供高级翻译功能
     * POST /v1/translate/premium-selection
     */
    @PostMapping(value = "/premium-selection")
    @PreAuthorize("isAuthenticated()")
    public SelectionTranslateResponse premiumTranslateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        Long userId = SecurityUtil.getRequiredUserId();
        return translatePort.selectionTranslate(userId, req);
    }

    /**
     * 为认证用户提供高级阅读器翻译
     * POST /v1/translate/premium-reader
     */
    @PostMapping(value = "/premium-reader")
    @PreAuthorize("isAuthenticated()")
    public ReaderTranslateResponse premiumTranslateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        Long userId = SecurityUtil.getRequiredUserId();
        return translatePort.readerTranslate(userId, req);
    }
}
