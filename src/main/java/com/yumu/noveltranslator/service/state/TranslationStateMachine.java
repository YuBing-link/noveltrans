package com.yumu.noveltranslator.service.state;

import com.yumu.noveltranslator.enums.TranslationStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 翻译任务状态机
 * 合法转换:
 *   PENDING    → {PROCESSING}
 *   PROCESSING → {COMPLETED, FAILED}
 *   FAILED     → {PENDING} (重试)
 */
@Component
public class TranslationStateMachine {

    private static final Map<TranslationStatus, Set<TranslationStatus>> VALID_TRANSITIONS = Map.of(
            TranslationStatus.PENDING, Set.of(TranslationStatus.PROCESSING),
            TranslationStatus.PROCESSING, Set.of(TranslationStatus.COMPLETED, TranslationStatus.FAILED),
            TranslationStatus.FAILED, Set.of(TranslationStatus.PENDING)
    );

    public void validateTransition(TranslationStatus current, TranslationStatus target) {
        if (!VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("非法状态转换: " + current + " → " + target);
        }
    }
}
