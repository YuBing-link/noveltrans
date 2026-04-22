package com.yumu.noveltranslator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslationPhaseTest {

    @Nested
    @DisplayName("枚举常量")
    class EnumConstantsTests {

        @Test
        void 存在START和STEADY常量() {
            TranslationPhase start = TranslationPhase.valueOf("START");
            TranslationPhase steady = TranslationPhase.valueOf("STEADY");

            assertNotNull(start);
            assertNotNull(steady);
        }

        @Test
        void values返回两个常量() {
            TranslationPhase[] values = TranslationPhase.values();

            assertEquals(2, values.length);
            assertTrue(java.util.List.of(values).contains(TranslationPhase.START));
            assertTrue(java.util.List.of(values).contains(TranslationPhase.STEADY));
        }

        @Test
        void valueOf无效名称抛出异常() {
            assertThrows(IllegalArgumentException.class, () -> TranslationPhase.valueOf("INVALID"));
        }
    }
}
