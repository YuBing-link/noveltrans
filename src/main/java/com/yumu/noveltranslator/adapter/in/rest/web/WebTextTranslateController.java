package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.port.dto.translation.SelectionTranslationRequest;
import com.yumu.noveltranslator.port.in.TextStreamEventConsumer;
import com.yumu.noveltranslator.port.in.TranslatePort;
import com.yumu.noveltranslator.util.SecurityUtil;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Web 端文本翻译接口
 * 路径前缀: /v1/translate
 */
@RestController
@RequestMapping("/v1/translate")
@RequiredArgsConstructor
@Slf4j
public class WebTextTranslateController {

    private final TranslatePort translatePort;

    /**
     * 文本流式翻译（SSE）— Web 首页使用的翻译接口
     * POST /v1/translate/text/stream
     */
    @PostMapping(value = "/text/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTextTranslate(@RequestBody @Valid SelectionTranslationRequest req) {
        Long userId = SecurityUtil.getRequiredUserId();
        log.info("[STREAM-TRACE] Controller entry: /v1/translate/text/stream, userId={}, engine={}, targetLang={}, mode={}, textLen={}",
                userId, req.getEngine(), req.getTargetLang(), req.getMode(), req.getText() != null ? req.getText().length() : 0);
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(300000L);
        TextStreamEventConsumer consumer = wrapEmitter(emitter);
        translatePort.streamTextTranslate(userId, req, consumer);
        return emitter;
    }

    private TextStreamEventConsumer wrapEmitter(SseEmitter emitter) {
        return event -> {
            if (event.isDone()) {
                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);
            } else if (event.isError()) {
                SseEmitterUtil.sendError(emitter, event.getText());
            } else {
                SseEmitterUtil.sendData(emitter, event.getText());
            }
        };
    }
}
