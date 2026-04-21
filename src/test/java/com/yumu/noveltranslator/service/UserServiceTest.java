package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.yumu.noveltranslator.util.PasswordUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private TranslationHistoryMapper translationHistoryMapper;
    @Mock private GlossaryMapper glossaryMapper;
    @Mock private UserPreferenceMapper userPreferenceMapper;
    @Mock private TranslationLimitProperties limitProperties;
    @Mock private QuotaService quotaService;

    private UserService createUserService() {
        return new UserService(userMapper, translationHistoryMapper, glossaryMapper,
                userPreferenceMapper, limitProperties, quotaService);
    }

    @Nested
    @DisplayName("用户统计")
    class UserStatisticsTests {

        @Test
        void 返回完整统计数据() {
            UserService userService = createUserService();
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(50);
            when(translationHistoryMapper.countByUserIdAndType(1L, "text")).thenReturn(30);
            when(translationHistoryMapper.countByUserIdAndType(1L, "document")).thenReturn(20);
            when(translationHistoryMapper.sumSourceTextLengthByUserId(1L)).thenReturn(10000L);
            when(translationHistoryMapper.countByUserIdAfter(anyLong(), any())).thenReturn(10);

            var response = userService.getUserStatistics(1L);

            assertEquals(50, response.getTotalTranslations());
            assertEquals(30, response.getTextTranslations());
            assertEquals(20, response.getDocumentTranslations());
            assertEquals(10000L, response.getTotalCharacters());
        }
    }

    @Nested
    @DisplayName("用户配额")
    class UserQuotaTests {

        @Test
        void 返回配额信息() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(quotaService.getMonthlyQuota("free")).thenReturn(10000L);
            when(quotaService.getUsedThisMonth(1L)).thenReturn(3000L);
            when(limitProperties.getFreeConcurrencyLimit()).thenReturn(5);
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);

            var response = userService.getUserQuota(user);

            assertEquals("FREE", response.getUserLevel());
            assertEquals(10000L, response.getMonthlyChars());
            assertEquals(3000L, response.getUsedThisMonth());
            assertEquals(7000L, response.getRemainingChars());
        }
    }

    @Nested
    @DisplayName("术语库")
    class GlossaryTests {

        @Test
        void 创建术语项成功() {
            UserService userService = createUserService();
            when(glossaryMapper.insert(any(Glossary.class))).thenReturn(1);

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord("hello");
            req.setTargetWord("你好");
            req.setRemark("greeting");

            GlossaryResponse result = userService.createGlossaryItem(1L, req);

            assertNotNull(result);
            assertEquals("hello", result.getSourceWord());
            assertEquals("你好", result.getTargetWord());
        }

        @Test
        void 删除术语项成功() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);
            when(glossaryMapper.deleteById(1L)).thenReturn(1);

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertTrue(result);
        }

        @Test
        void 删除非自己的术语项失败() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("用户偏好")
    class UserPreferencesTests {

        @Test
        void 首次设置创建新偏好() {
            UserService userService = createUserService();
            when(userPreferenceMapper.findByUserId(1L)).thenReturn(null);
            when(userPreferenceMapper.insert(any(UserPreference.class))).thenReturn(1);

            UserPreferencesRequest req = new UserPreferencesRequest();
            req.setDefaultEngine("deepl");
            req.setDefaultTargetLang("en");
            req.setEnableGlossary(true);
            req.setEnableCache(true);
            req.setFontSize(16);
            req.setThemeMode("dark");

            UserPreferencesResponse result = userService.updateUserPreferences(1L, req);

            assertNotNull(result);
            assertEquals("deepl", result.getDefaultEngine());
            assertEquals("en", result.getDefaultTargetLang());
            assertEquals(16, result.getFontSize());
            assertEquals("dark", result.getThemeMode());
        }

        @Test
        void 获取不存在的偏好返回默认值() {
            UserService userService = createUserService();
            when(userPreferenceMapper.findByUserId(1L)).thenReturn(null);

            UserPreferencesResponse result = userService.getUserPreferences(1L);

            assertNotNull(result);
            assertEquals("google", result.getDefaultEngine());
            assertEquals("zh", result.getDefaultTargetLang());
            assertEquals(14, result.getFontSize());
        }
    }

    @Nested
    @DisplayName("平台统计")
    class PlatformStatsTests {

        @Test
        void 返回完整平台统计() {
            UserService userService = createUserService();
            when(userMapper.countActiveUsers()).thenReturn(1000);
            when(translationHistoryMapper.countActiveUsersAfter(any())).thenReturn(100);
            when(translationHistoryMapper.countAll()).thenReturn(50000L);
            when(translationHistoryMapper.countAfter(any())).thenReturn(500L);
            when(translationHistoryMapper.sumAllSourceTextLength()).thenReturn(1000000L);
            when(translationHistoryMapper.countDocumentTranslations()).thenReturn(200);
            when(glossaryMapper.selectCount(any())).thenReturn(500L);

            var response = userService.getPlatformStats();

            assertEquals(1000, response.getTotalUsers());
            assertEquals(50000L, response.getTotalTranslations());
            assertEquals(1000000L, response.getTotalCharacters());
            assertEquals("normal", response.getSystemStatus());
        }
    }
}
