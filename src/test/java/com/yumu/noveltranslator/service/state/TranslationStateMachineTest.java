package com.yumu.noveltranslator.service.state;

import com.yumu.noveltranslator.enums.TranslationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationStateMachine 测试")
class TranslationStateMachineTest {

    private final TranslationStateMachine sm = new TranslationStateMachine();

    @Test
    void PENDING到PROCESSING合法() {
        assertDoesNotThrow(() -> sm.validateTransition(TranslationStatus.PENDING, TranslationStatus.PROCESSING));
    }

    @Test
    void PROCESSING到COMPLETED合法() {
        assertDoesNotThrow(() -> sm.validateTransition(TranslationStatus.PROCESSING, TranslationStatus.COMPLETED));
    }

    @Test
    void PROCESSING到FAILED合法() {
        assertDoesNotThrow(() -> sm.validateTransition(TranslationStatus.PROCESSING, TranslationStatus.FAILED));
    }

    @Test
    void FAILED到PENDING合法() {
        assertDoesNotThrow(() -> sm.validateTransition(TranslationStatus.FAILED, TranslationStatus.PENDING));
    }

    @Test
    void PENDING到COMPLETED非法() {
        assertThrows(IllegalStateException.class, () -> sm.validateTransition(TranslationStatus.PENDING, TranslationStatus.COMPLETED));
    }

    @Test
    void PROCESSING到PENDING非法() {
        assertThrows(IllegalStateException.class, () -> sm.validateTransition(TranslationStatus.PROCESSING, TranslationStatus.PENDING));
    }

    @Test
    void COMPLETED到任意状态非法() {
        assertThrows(IllegalStateException.class, () -> sm.validateTransition(TranslationStatus.COMPLETED, TranslationStatus.PENDING));
        assertThrows(IllegalStateException.class, () -> sm.validateTransition(TranslationStatus.COMPLETED, TranslationStatus.PROCESSING));
    }

    @Test
    void FAILED到COMPLETED非法() {
        assertThrows(IllegalStateException.class, () -> sm.validateTransition(TranslationStatus.FAILED, TranslationStatus.COMPLETED));
    }
}
