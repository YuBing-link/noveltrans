package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.mapper.QuotaUsageMapper;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaServiceTest {

    @Mock
    private QuotaUsageMapper quotaUsageMapper;

    @Mock
    private TranslationLimitProperties limitProperties;

    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(quotaUsageMapper, limitProperties);
    }

    @Nested
    @DisplayName("获取月度配额")
    class GetMonthlyQuotaTests {

        @Test
        void null等级默认返回免费配额() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);

            long result = quotaService.getMonthlyQuota(null);

            assertEquals(10_000L, result);
            verify(limitProperties).getFreeMonthlyChars();
        }

        @Test
        void max等级返回max配额() {
            when(limitProperties.getMaxMonthlyChars()).thenReturn(200_000L);

            long result = quotaService.getMonthlyQuota("max");

            assertEquals(200_000L, result);
            verify(limitProperties).getMaxMonthlyChars();
        }

        @Test
        void pro等级返回pro配额() {
            when(limitProperties.getProMonthlyChars()).thenReturn(50_000L);

            long result = quotaService.getMonthlyQuota("pro");

            assertEquals(50_000L, result);
            verify(limitProperties).getProMonthlyChars();
        }

        @Test
        void free等级返回免费配额() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);

            long result = quotaService.getMonthlyQuota("free");

            assertEquals(10_000L, result);
            verify(limitProperties).getFreeMonthlyChars();
        }

        @Test
        void 大小写不敏感() {
            when(limitProperties.getMaxMonthlyChars()).thenReturn(200_000L);

            long result = quotaService.getMonthlyQuota("MAX");

            assertEquals(200_000L, result);
        }
    }

    @Nested
    @DisplayName("获取模式系数")
    class GetModeMultiplierTests {

        @Test
        void null模式默认返回专家系数() {
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);

            double result = quotaService.getModeMultiplier(null);

            assertEquals(1.0, result);
            verify(limitProperties).getExpertModeMultiplier();
        }

        @Test
        void fast模式返回快速系数() {
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);

            double result = quotaService.getModeMultiplier("fast");

            assertEquals(0.5, result);
            verify(limitProperties).getFastModeMultiplier();
        }

        @Test
        void team模式返回团队系数() {
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);

            double result = quotaService.getModeMultiplier("team");

            assertEquals(2.0, result);
            verify(limitProperties).getTeamModeMultiplier();
        }

        @Test
        void 大小写不敏感() {
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);

            double result = quotaService.getModeMultiplier("FAST");

            assertEquals(0.5, result);
        }
    }

    @Nested
    @DisplayName("查询本月已用字符数")
    class GetUsedThisMonthTests {

        @Test
        void 委托mapper查询() {
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(5_000L);

            long result = quotaService.getUsedThisMonth(1L);

            assertEquals(5_000L, result);
            verify(quotaUsageMapper).getMonthlyUsage(eq(1L), any(LocalDate.class));
        }
    }

    @Nested
    @DisplayName("查询剩余字符数")
    class GetRemainingCharsTests {

        @Test
        void 配额大于已用返回差值() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(3_000L);

            long result = quotaService.getRemainingChars(1L, "free");

            assertEquals(7_000L, result);
        }

        @Test
        void 配额小于等于已用返回0() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(12_000L);

            long result = quotaService.getRemainingChars(1L, "free");

            assertEquals(0L, result);
        }

        @Test
        void 配额等于已用返回0() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(10_000L);

            long result = quotaService.getRemainingChars(1L, "free");

            assertEquals(0L, result);
        }
    }

    @Nested
    @DisplayName("尝试消耗字符")
    class TryConsumeCharsTests {

        @Test
        void 配额充足成功扣减() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(3_000L);

            boolean result = quotaService.tryConsumeChars(1L, "free", 2_000L, "expert");

            assertTrue(result);
            verify(quotaUsageMapper).incrementUsage(eq(1L), any(LocalDate.class), eq(2_000L));
        }

        @Test
        void 配额不足返回失败() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(9_000L);

            boolean result = quotaService.tryConsumeChars(1L, "free", 2_000L, "expert");

            assertFalse(result);
            verify(quotaUsageMapper, never()).incrementUsage(anyLong(), any(), anyLong());
        }

        @Test
        void 消耗计算使用模式系数() {
            when(limitProperties.getFreeMonthlyChars()).thenReturn(10_000L);
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);
            when(quotaUsageMapper.getMonthlyUsage(eq(1L), any(LocalDate.class))).thenReturn(3_000L);

            // team模式: 2000 * 2.0 = 4000, 剩余 7000 >= 4000 成功
            boolean result = quotaService.tryConsumeChars(1L, "free", 2_000L, "team");

            assertTrue(result);
            verify(quotaUsageMapper).incrementUsage(eq(1L), any(LocalDate.class), eq(4_000L));
        }
    }
}
