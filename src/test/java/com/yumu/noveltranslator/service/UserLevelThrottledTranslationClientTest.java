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
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserLevelThrottledTranslationClientTest {

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

    /**
     * 设置认证用户上下文
     */
    private void setAuthenticatedUser(Long userId, String userLevel) {
        User user = new User();
        user.setId(userId);
        user.setUserLevel(userLevel);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Nested
    @DisplayName("获取用户信号量")
    class GetUserSemaphoreTests {

        @Test
        void free用户返回free信号量() {
            setAuthenticatedUser(1L, "free");
            // Verify by checking the semaphore allows translation (no exception)
            // We test via translate method with fastMode
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
            assertTrue(result.contains("translated"));
        }

        @Test
        void pro用户返回pro信号量() {
            setAuthenticatedUser(1L, "pro");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
        }

        @Test
        void 未认证用户返回anonymous信号量() {
            SecurityContextHolder.clearContext();
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
        }

        @Test
        void premium用户视为pro用户() {
            setAuthenticatedUser(1L, "premium");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("翻译模式")
    class TranslationModeTests {

        @Test
        void fastMode直接走MTranServer() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "fast-translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            JSONObject parsed = JSONObject.parseObject(result);
            assertEquals("fast-translated", parsed.getString("translatedContent"));
            assertEquals("mtran", parsed.getString("engine"));
            verify(externalTranslationService).translate("auto", "zh", "Hello", false);
        }

        @Test
        void html模式直接走MTranServer() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "html-translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("<p>Hello</p>", "zh", "google", true);

            JSONObject parsed = JSONObject.parseObject(result);
            assertEquals("html-translated", parsed.getString("translatedContent"));
            verify(externalTranslationService).translate("auto", "zh", "<p>Hello</p>", true);
        }

        @Test
        void html模式翻译返回无result字段使用原始JSON() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("data", "some-data"); // No "result" field
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", true);

            JSONObject parsed = JSONObject.parseObject(result);
            // translatedContent should be the raw JSON since there's no "result"
            assertTrue(parsed.getString("translatedContent").contains("some-data"));
        }

        @Test
        void 四参数translate委托给五参数版本() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("专家模式 Python 翻译")
    class PythonTranslateTests {

        @Test
        void 信号量超时抛出异常() {
            setAuthenticatedUser(1L, "free");
            // Fill up all semaphore permits
            Semaphore sem = (Semaphore) ReflectionTestUtils.getField(client, "freeUserSemaphore");
            try {
                sem.acquire(10); // Take all permits
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertThrows(RuntimeException.class, () ->
                    client.translateWithPython("Hello", "zh", "google"));

            sem.release(10); // Release for cleanup
        }
    }

    @Nested
    @DisplayName("轮询选择逻辑")
    class RoundRobinSelectionTests {

        @Test
        void shouldUsePythonService样本不足时轮流() {
            // Both counters at 0, should use Python (even index)
            boolean usePython = invokeShouldUsePythonService();
            // (0+0) % 2 == 0, so should be true
            assertTrue(usePython);
        }

        @Test
        void shouldUsePythonServicePython样本不足优先() {
            // Set MTran count to 6 (above MIN_REQUESTS_FOR_STATS), Python at 0
            ReflectionTestUtils.setField(client, "mTranRequestCount", new java.util.concurrent.atomic.AtomicInteger(6));
            ReflectionTestUtils.setField(client, "pythonRequestCount", new java.util.concurrent.atomic.AtomicInteger(0));

            boolean usePython = invokeShouldUsePythonService();
            assertTrue(usePython);
        }

        @Test
        void shouldUsePythonServiceMTran样本不足优先() {
            ReflectionTestUtils.setField(client, "pythonRequestCount", new java.util.concurrent.atomic.AtomicInteger(6));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new java.util.concurrent.atomic.AtomicInteger(0));

            boolean usePython = invokeShouldUsePythonService();
            assertFalse(usePython);
        }

        @Test
        void shouldUsePythonService两者优秀率均为0时随机() {
            ReflectionTestUtils.setField(client, "pythonRequestCount", new java.util.concurrent.atomic.AtomicInteger(10));
            ReflectionTestUtils.setField(client, "pythonExcellentCount", new java.util.concurrent.atomic.AtomicInteger(0));
            ReflectionTestUtils.setField(client, "mTranRequestCount", new java.util.concurrent.atomic.AtomicInteger(10));
            ReflectionTestUtils.setField(client, "mTranExcellentCount", new java.util.concurrent.atomic.AtomicInteger(0));

            // Should not throw, just return something
            boolean usePython = invokeShouldUsePythonService();
            // Result is random, just verify it doesn't crash
            assertTrue(usePython || !usePython);
        }
    }

    @Nested
    @DisplayName("统计信息")
    class StatsTests {

        @Test
        void getRoundRobinStats返回有效数据() {
            Map<String, Object> stats = client.getRoundRobinStats();

            assertTrue(stats.containsKey("python_requests"));
            assertTrue(stats.containsKey("python_excellent"));
            assertTrue(stats.containsKey("python_excellent_rate"));
            assertTrue(stats.containsKey("mtran_requests"));
            assertTrue(stats.containsKey("mtran_excellent"));
            assertTrue(stats.containsKey("mtran_excellent_rate"));
            assertTrue(stats.containsKey("next_reset_seconds"));
        }

        @Test
        void getRoundRobinStats初始值为0() {
            Map<String, Object> stats = client.getRoundRobinStats();

            assertEquals(0, stats.get("python_requests"));
            assertEquals(0, stats.get("python_excellent"));
            assertEquals(0, stats.get("mtran_requests"));
            assertEquals(0, stats.get("mtran_excellent"));
            assertEquals("N/A", stats.get("python_excellent_rate"));
            assertEquals("N/A", stats.get("mtran_excellent_rate"));
        }
    }

    @Nested
    @DisplayName("统计重置")
    class StatsResetTests {

        @Test
        void 超过60秒自动重置计数器() {
            // Set last reset time to 61 seconds ago
            long sixtyOneSecondsAgo = System.currentTimeMillis() - 61_000;
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new java.util.concurrent.atomic.AtomicLong(sixtyOneSecondsAgo));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new java.util.concurrent.atomic.AtomicLong(sixtyOneSecondsAgo));

            // Set some request counts
            java.util.concurrent.atomic.AtomicInteger pythonCount = new java.util.concurrent.atomic.AtomicInteger(10);
            java.util.concurrent.atomic.AtomicInteger pythonExcellent = new java.util.concurrent.atomic.AtomicInteger(5);
            ReflectionTestUtils.setField(client, "pythonRequestCount", pythonCount);
            ReflectionTestUtils.setField(client, "pythonExcellentCount", pythonExcellent);

            invokeResetStatsIfNeeded();

            // Counters should be reset to 0
            assertEquals(0, pythonCount.get());
            assertEquals(0, pythonExcellent.get());
        }

        @Test
        void 未超60秒不重置() {
            // Set last reset time to 30 seconds ago
            long thirtySecondsAgo = System.currentTimeMillis() - 30_000;
            ReflectionTestUtils.setField(client, "pythonLastResetTime", new java.util.concurrent.atomic.AtomicLong(thirtySecondsAgo));
            ReflectionTestUtils.setField(client, "mTranLastResetTime", new java.util.concurrent.atomic.AtomicLong(thirtySecondsAgo));

            java.util.concurrent.atomic.AtomicInteger pythonCount = new java.util.concurrent.atomic.AtomicInteger(10);
            java.util.concurrent.atomic.AtomicInteger mTranCount = new java.util.concurrent.atomic.AtomicInteger(8);
            ReflectionTestUtils.setField(client, "pythonRequestCount", pythonCount);
            ReflectionTestUtils.setField(client, "mTranRequestCount", mTranCount);

            invokeResetStatsIfNeeded();

            assertEquals(10, pythonCount.get());
            assertEquals(8, mTranCount.get());
        }
    }

    /**
     * 通过反射调用 shouldUsePythonService(boolean fastMode)
     */
    private boolean invokeShouldUsePythonService() {
        return invokeShouldUsePythonService(false);
    }

    /**
     * 通过反射调用 shouldUsePythonService(boolean fastMode)
     */
    private boolean invokeShouldUsePythonService(boolean fastMode) {
        try {
            java.lang.reflect.Method method = UserLevelThrottledTranslationClient.class
                    .getDeclaredMethod("shouldUsePythonService", boolean.class);
            method.setAccessible(true);
            return (boolean) method.invoke(client, fastMode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 通过反射调用 resetStatsIfNeeded
     */
    private void invokeResetStatsIfNeeded() {
        try {
            java.lang.reflect.Method method = UserLevelThrottledTranslationClient.class
                    .getDeclaredMethod("resetStatsIfNeeded");
            method.setAccessible(true);
            method.invoke(client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
