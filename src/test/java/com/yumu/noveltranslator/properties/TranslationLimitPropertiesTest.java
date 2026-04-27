package com.yumu.noveltranslator.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationLimitProperties 测试")
class TranslationLimitPropertiesTest {

    private TranslationLimitProperties props;

    @BeforeEach
    void setUp() {
        props = new TranslationLimitProperties();
    }

    @Test
    void 默认免费每日限制() {
        assertEquals(500, props.getFreeDailyLimit());
    }

    @Test
    void 默认专业每日限制() {
        assertEquals(5000, props.getProDailyLimit());
    }

    @Test
    void 默认并发限制() {
        assertEquals(1, props.getFreeConcurrencyLimit());
        assertEquals(3, props.getProConcurrencyLimit());
        assertEquals(1, props.getAnonymousConcurrencyLimit());
    }

    @Test
    void 默认月度字符配额() {
        assertEquals(100_000, props.getFreeMonthlyChars());
        assertEquals(500_000, props.getProMonthlyChars());
        assertEquals(2_000_000, props.getMaxMonthlyChars());
    }

    @Test
    void 默认模式系数() {
        assertEquals(0.5, props.getFastModeMultiplier());
        assertEquals(1.0, props.getExpertModeMultiplier());
        assertEquals(2.0, props.getTeamModeMultiplier());
    }

    @Test
    void setter工作正常() {
        props.setFreeDailyLimit(50);
        assertEquals(50, props.getFreeDailyLimit());
        props.setFreeMonthlyChars(5000);
        assertEquals(5000, props.getFreeMonthlyChars());
        props.setFastModeMultiplier(0.3);
        assertEquals(0.3, props.getFastModeMultiplier());
    }
}
