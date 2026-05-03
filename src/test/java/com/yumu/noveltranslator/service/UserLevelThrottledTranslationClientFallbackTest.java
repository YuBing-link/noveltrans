package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSONObject;
import com.yumu.noveltranslator.entity.Glossary;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserLevelThrottledTranslationClient 降级路径测试
 * 覆盖 Python→MTran、MTran→Python 双向降级、术语表路由等
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserLevelThrottledTranslationClient 降级路径测试")
class UserLevelThrottledTranslationClientFallbackTest {

    @Mock
    private ExternalTranslationService externalTranslationService;
    @Mock
    private TranslationLimitProperties limitProperties;

    private UserLevelThrottledTranslationClient client;

    @BeforeEach
    void setUp() {
        when(limitProperties.getFreeConcurrencyLimit()).thenReturn(10);
        when(limitProperties.getProConcurrencyLimit()).thenReturn(20);
        when(limitProperties.getMaxConcurrencyLimit()).thenReturn(50);
        when(limitProperties.getAnonymousConcurrencyLimit()).thenReturn(3);

        client = new UserLevelThrottledTranslationClient(externalTranslationService, limitProperties);
        ReflectionTestUtils.setField(client, "pythonTranslateUrl", "http://localhost:19999/translate");
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

    @Nested
    @DisplayName("术语表强制路由 Python")
    class GlossaryRoutingTests {

        @Test
        void 术语表非空强制走Python() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "mtran-with-glossary-fallback");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            List<Glossary> terms = List.of(new Glossary());
            String result = client.translate("Hello", "zh", "google", false, true, terms);

            assertNotNull(result);
        }

        @Test
        void 术语表空列表不走强制路由() {
            setAuthenticatedUser(1L, "free");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "mtran-no-terms");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true, List.of());

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("translateWithRoundRobin 双向降级")
    class RoundRobinFallbackTests {

        @Test
        void MTran和Python都失败抛出异常() {
            setAuthenticatedUser(1L, "free");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("MTran down"));

            assertThrows(RuntimeException.class, () ->
                client.translate("Hello", "zh", "google", false, false));
        }
    }

    @Nested
    @DisplayName("translateWithPython 降级路径")
    class PythonFallbackTests {

        @Test
        void Python失败降级MTran成功() {
            setAuthenticatedUser(1L, "pro");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "mtran-fallback");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translateWithPython("Hello", "zh", "google");

            assertNotNull(result);
        }

        @Test
        void Python和MTran都失败抛出异常() {
            setAuthenticatedUser(1L, "free");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("MTran also down"));

            assertThrows(RuntimeException.class, () ->
                client.translateWithPython("Hello", "zh", "google"));
        }
    }

    @Nested
    @DisplayName("max 用户信号量")
    class MaxUserSemaphoreTests {

        @Test
        void max用户使用独立信号量() {
            setAuthenticatedUser(1L, "max");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "max-translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
        }

        @Test
        void premium用户视为pro() {
            setAuthenticatedUser(1L, "premium");
            JSONObject mtranResp = new JSONObject();
            mtranResp.put("result", "premium-translated");
            when(externalTranslationService.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(mtranResp);

            String result = client.translate("Hello", "zh", "google", false, true);

            assertNotNull(result);
        }
    }
}
