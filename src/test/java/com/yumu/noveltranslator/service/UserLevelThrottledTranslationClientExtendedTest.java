package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSONObject;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserLevelThrottledTranslationClient 补充测试
 * 覆盖现有测试未覆盖的分支：translateWithFallback MTranServer 降级成功、
 * shouldUsePythonService 基于优秀率的概率选择、recordStats 等
 *
 * 注意：doTranslateRequest 是 private 且调用 HTTP，无法 mock。
 * 因此通过 ExternalTranslationService mock 覆盖 MTranServer 路径，
 * 通过反射调用 shouldUsePythonService 覆盖概率计算。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserLevelThrottledTranslationClient 补充测试")
class UserLevelThrottledTranslationClientExtendedTest {

    @Mock
    private ExternalTranslationService externalTranslationService;
    @Mock
    private TranslationLimitProperties limitProperties;

    private UserLevelThrottledTranslationClient client;

    @BeforeEach
    void setUp() {
        when(limitProperties.getFreeConcurrencyLimit()).thenReturn(10);
        when(limitProperties.getProConcurrencyLimit()).thenReturn(20);
        when(limitProperties.getAnonymousConcurrencyLimit()).thenReturn(3);

        client = new UserLevelThrottledTranslationClient(externalTranslationService, limitProperties);
        ReflectionTestUtils.setField(client, "pythonTranslateUrl", "http://localhost:9999/translate");
        client.init();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId, String userLevel) {
        User user = new User();
        user.setId(userId);
        user.setUserLevel(userLevel);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // ============ translate 正常模式 MTranServer 路径 ============

    @Nested
    @DisplayName("translate - 正常模式走轮询 MTranServer")
    class TranslateNormalModeMTranTests {

        @Test
        void 正常模式MTranServer成功翻译() {
            setAuthenticatedUser(1L, "free");

            // 确保 shouldUsePythonService 返回 false → 走 MTranServer
            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(0));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "MTran正常模式翻译");
            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, false);

            assertNotNull(result);
            assertTrue(result.contains("MTran正常模式翻译"));
        }

        @Test
        void MTranServer失败后Python也不可用抛出异常() {
            setAuthenticatedUser(1L, "free");

            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(0));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenThrow(new RuntimeException("MTran down"));

            assertThrows(RuntimeException.class, () ->
                    client.translate("Hello", "zh", "google", false, false));
        }
    }

    // ============ translateWithPython MTranServer 降级路径 ============

    @Nested
    @DisplayName("translateWithPython - MTranServer 降级")
    class TranslateWithPythonFallbackTests {

        @Test
        void Python不可用降级到MTranServer成功() {
            setAuthenticatedUser(1L, "free");

            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "Python降级MTran");
            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenReturn(mtranResp);

            String result = client.translateWithPython("Hello", "zh", "google");

            assertNotNull(result);
            assertTrue(result.contains("Python降级MTran"));
        }

        @Test
        void Python和MTranServer都失败抛出异常() {
            setAuthenticatedUser(1L, "free");

            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenThrow(new RuntimeException("MTran down"));

            assertThrows(RuntimeException.class, () ->
                    client.translateWithPython("Hello", "zh", "google"));
        }
    }

    // ============ translate 四参数版本 ============

    @Nested
    @DisplayName("translate 四参数 - fastMode=false 走轮询")
    class TranslateFourParamsTests {

        @Test
        void 四参数非fastMode走MTranServer() {
            setAuthenticatedUser(1L, "free");

            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(0));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "四参数翻译");
            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false);

            assertNotNull(result);
            assertTrue(result.contains("四参数翻译"));
        }
    }

    // ============ shouldUsePythonService 基于优秀率 ============

    @Nested
    @DisplayName("shouldUsePythonService - 优秀率计算")
    class ShouldUsePythonServiceExcellentTests {

        @Test
        void Python优秀率高于MTran时更大概率选Python() {
            // Python: 10 请求，8 优秀 (80%)
            // MTran: 10 请求，2 优秀 (20%)
            // Python 概率 = 0.8 / (0.8 + 0.2) = 80%
            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(8));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(2));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            // 运行多次，统计 Python 被选中的比例
            int pythonCount = 0;
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                if (invokeShouldUsePythonService()) {
                    pythonCount++;
                }
            }

            // 80% 概率，100 次中应有 50-100 次（保守区间）
            assertTrue(pythonCount >= 50, "Python should be selected more often with higher excellent rate, got " + pythonCount);
        }

        @Test
        void 两者优秀率相同时概率约50() {
            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(5));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(5));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            int pythonCount = 0;
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                if (invokeShouldUsePythonService()) {
                    pythonCount++;
                }
            }

            // 50% 概率，100 次中应有 20-80 次（宽松区间）
            assertTrue(pythonCount >= 20 && pythonCount <= 80, "Selection should be roughly 50/50, got " + pythonCount);
        }

        @Test
        void Python全优时100选Python() {
            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(10));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(0));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            // Python 100% 优秀率，MTran 0% → Python 概率 = 1.0
            int pythonCount = 0;
            int iterations = 50;
            for (int i = 0; i < iterations; i++) {
                if (invokeShouldUsePythonService()) {
                    pythonCount++;
                }
            }
            assertEquals(iterations, pythonCount, "Python should always be selected with 100% excellent rate");
        }
    }

    // ============ getRoundRobinStats 有数据时 ============

    @Nested
    @DisplayName("getRoundRobinStats - 有请求数据")
    class GetRoundRobinStatsWithDataTests {

        @Test
        void 翻译后统计显示有效数据() {
            setAuthenticatedUser(1L, "free");

            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(50));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(30));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            Map<String, Object> stats = client.getRoundRobinStats();

            assertEquals(100, stats.get("python_requests"));
            assertEquals(50, stats.get("python_excellent"));
            assertEquals("50.00%", stats.get("python_excellent_rate"));
            assertEquals(100, stats.get("mtran_requests"));
            assertEquals(30, stats.get("mtran_excellent"));
            assertEquals("30.00%", stats.get("mtran_excellent_rate"));
        }

        @Test
        void 部分请求无优秀记录显示正确比例() {
            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(5));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(0));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(3));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(1));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            Map<String, Object> stats = client.getRoundRobinStats();

            assertEquals("0.00%", stats.get("python_excellent_rate"));
            assertEquals("33.33%", stats.get("mtran_excellent_rate"));
        }
    }

    // ============ recordStats 间接验证 ============

    @Nested
    @DisplayName("recordStats - 翻译后统计增加")
    class RecordStatsTests {

        @Test
        void MTranServer翻译成功后请求计数加一() {
            setAuthenticatedUser(1L, "free");

            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(50));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(50));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "ok");
            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenReturn(mtranResp);

            client.translate("Hello", "zh", "google", false, false);

            int mTranCount = ((AtomicInteger) ReflectionTestUtils.getField(client, "mTranRequestCount")).get();
            assertEquals(101, mTranCount);
        }

        @Test
        void MTranServer翻译失败请求计数仍加一() {
            setAuthenticatedUser(1L, "free");

            ReflectionTestUtils.setField(client, "pythonRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new AtomicInteger(50));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new AtomicInteger(100));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new AtomicInteger(50));
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new AtomicLong(System.currentTimeMillis()));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new AtomicLong(System.currentTimeMillis()));

            when(externalTranslationService.translate("auto", "zh", "Hello", false))
                    .thenThrow(new RuntimeException("fail"));

            assertThrows(RuntimeException.class, () ->
                    client.translate("Hello", "zh", "google", false, false));

            // Even on failure, the request count is incremented
            int mTranCount = ((AtomicInteger) ReflectionTestUtils.getField(client, "mTranRequestCount")).get();
            assertEquals(101, mTranCount);
        }
    }

    // ============ 反射辅助方法 ============

    private boolean invokeShouldUsePythonService() {
        try {
            java.lang.reflect.Method method = UserLevelThrottledTranslationClient.class
                    .getDeclaredMethod("shouldUsePythonService");
            method.setAccessible(true);
            return (boolean) method.invoke(client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
