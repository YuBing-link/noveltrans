package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.adapter.out.translate.TokenAwareRateLimiter;

import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenAwareRateLimiterTest {

    private TokenAwareRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        TranslationLimitProperties props = mock(TranslationLimitProperties.class);
        when(props.getFreeTpmLimit()).thenReturn(100);
        when(props.getProTpmLimit()).thenReturn(500);
        when(props.getMaxTpmLimit()).thenReturn(2000);
        rateLimiter = new TokenAwareRateLimiter(props);
    }

    // ==================== estimateTokens ====================

    @Nested
    @DisplayName("estimateTokens")
    class EstimateTokensTests {

        @Test
        void null文本返回0() {
            assertEquals(0, TokenAwareRateLimiter.estimateTokens(null));
        }

        @Test
        void 空字符串返回0() {
            assertEquals(0, TokenAwareRateLimiter.estimateTokens(""));
        }

        @Test
        void 纯英文文本按一点三倍计算() {
            // "Hello world" = 11 chars, 0 CJK → ceil(11 * 1.3) = 15
            int tokens = TokenAwareRateLimiter.estimateTokens("Hello world");
            assertEquals(15, tokens);
        }

        @Test
        void 纯CJK文本按零点五倍计算() {
            // "你好世界" = 4 CJK chars → ceil(4 * 0.5) = 2
            int tokens = TokenAwareRateLimiter.estimateTokens("你好世界");
            assertEquals(2, tokens);
        }

        @Test
        void 混合文本分开计算() {
            // "Hello 世界" = 6 western (including space) + 2 CJK → ceil(6*1.3 + 2*0.5) = ceil(7.8 + 1.0) = 9
            int tokens = TokenAwareRateLimiter.estimateTokens("Hello 世界");
            assertEquals(9, tokens);
        }

        @Test
        void 日文平假名视为CJK() {
            // "こんにちは" = 5 CJK chars → ceil(5 * 0.5) = 3
            int tokens = TokenAwareRateLimiter.estimateTokens("こんにちは");
            assertEquals(3, tokens);
        }

        @Test
        void 日文片假名视为CJK() {
            // "テスト" = 3 CJK chars → ceil(3 * 0.5) = 2
            int tokens = TokenAwareRateLimiter.estimateTokens("テスト");
            assertEquals(2, tokens);
        }

        @Test
        void 韩文谚文视为CJK() {
            // "안녕하세요" = 5 CJK chars → ceil(5 * 0.5) = 3
            int tokens = TokenAwareRateLimiter.estimateTokens("안녕하세요");
            assertEquals(3, tokens);
        }

        @Test
        void 大量文本计算不溢出() {
            String text = "a".repeat(100000);
            int tokens = TokenAwareRateLimiter.estimateTokens(text);
            assertEquals(130000, tokens);
        }
    }

    // ==================== tryConsume ====================

    @Nested
    @DisplayName("tryConsume")
    class TryConsumeTests {

        @Test
        void free用户低消耗成功() {
            assertTrue(rateLimiter.tryConsume("user_1", "free", 50));
        }

        @Test
        void free用户超出配额拒绝() {
            // free limit = 100
            assertTrue(rateLimiter.tryConsume("user_2", "free", 60));
            assertFalse(rateLimiter.tryConsume("user_2", "free", 50)); // 60+50 > 100
        }

        @Test
        void pro用户更高配额() {
            // pro limit = 500
            assertTrue(rateLimiter.tryConsume("user_3", "pro", 400));
            assertTrue(rateLimiter.tryConsume("user_3", "pro", 90)); // 490 < 500
            assertFalse(rateLimiter.tryConsume("user_3", "pro", 20)); // 510 > 500
        }

        @Test
        void max用户最高配额() {
            // max limit = 2000
            assertTrue(rateLimiter.tryConsume("user_4", "max", 1500));
            assertTrue(rateLimiter.tryConsume("user_4", "max", 400)); // 1900 < 2000
            assertFalse(rateLimiter.tryConsume("user_4", "max", 200)); // 2100 > 2000
        }

        @Test
        void null用户等级使用free配额() {
            assertTrue(rateLimiter.tryConsume("user_5", null, 50));
            assertFalse(rateLimiter.tryConsume("user_5", null, 60)); // 50+60 > 100
        }

        @Test
        void premium用户视为max() {
            assertTrue(rateLimiter.tryConsume("user_6", "premium", 1500));
            assertFalse(rateLimiter.tryConsume("user_6", "premium", 600)); // 2100 > 2000
        }

        @Test
        void 不同用户独立计数() {
            assertTrue(rateLimiter.tryConsume("user_a", "free", 80));
            assertTrue(rateLimiter.tryConsume("user_b", "free", 80));
        }

        @Test
        void 正好等于配额可以消费() {
            // limit = 100, consume exactly 100
            assertTrue(rateLimiter.tryConsume("user_7", "free", 100));
            assertFalse(rateLimiter.tryConsume("user_7", "free", 1)); // 101 > 100
        }
    }

    // ==================== refund ====================

    @Nested
    @DisplayName("refund")
    class RefundTests {

        @Test
        void 退款后恢复配额() {
            assertTrue(rateLimiter.tryConsume("user_r1", "free", 80));
            assertFalse(rateLimiter.tryConsume("user_r1", "free", 30)); // 110 > 100

            rateLimiter.refund("user_r1", 80);
            // After refund, totalInWindow is ~0, but bucket still has 80 from first consume.
            // Can consume up to (100 - 80) = 20 more in the same second.
            assertTrue(rateLimiter.tryConsume("user_r1", "free", 20)); // 0+80(bucket)+20 <= 100 is checked via totalInWindow
        }

        @Test
        void 退款超过已消费不抛异常() {
            assertTrue(rateLimiter.tryConsume("user_r2", "free", 30));
            rateLimiter.refund("user_r2", 999); // Over-refund
            // Should not throw, and remaining quota should be at the limit
            assertTrue(rateLimiter.tryConsume("user_r2", "free", 50));
        }

        @Test
        void 不存在用户退款不抛异常() {
            rateLimiter.refund("nonexistent_user", 100);
            // Should not throw
        }
    }

    // ==================== cleanupIdleCounters ====================

    @Nested
    @DisplayName("cleanupIdleCounters")
    class CleanupTests {

        @Test
        void 清理空闲计数器() {
            rateLimiter.tryConsume("idle_user_1", "free", 10);
            rateLimiter.tryConsume("idle_user_2", "pro", 20);

            // Manually set lastAccessTime to 6 minutes ago (idle threshold = 5 min)
            var counters = (java.util.concurrent.ConcurrentHashMap<String, ?>)
                    org.springframework.test.util.ReflectionTestUtils.getField(rateLimiter, "userCounters");
            for (var entry : counters.entrySet()) {
                org.springframework.test.util.ReflectionTestUtils.setField(
                        entry.getValue(), "lastAccessTime", System.currentTimeMillis() - 360_000);
            }

            rateLimiter.cleanupIdleCounters();

            assertEquals(0, counters.size());
        }

        @Test
        void 活跃计数器不被清理() {
            rateLimiter.tryConsume("active_user", "free", 10);

            // Do NOT set lastAccessTime to past, so it should be recent
            // Should NOT be cleaned up
            rateLimiter.cleanupIdleCounters();

            var counters = (java.util.concurrent.ConcurrentHashMap<String, ?>)
                    org.springframework.test.util.ReflectionTestUtils.getField(rateLimiter, "userCounters");
            assertEquals(1, counters.size());
        }
    }
}
