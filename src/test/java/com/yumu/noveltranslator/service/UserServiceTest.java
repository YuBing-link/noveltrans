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

import java.util.List;

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

    @Nested
    @DisplayName("术语库列表")
    class GlossaryListTests {

        @Test
        void 返回术语列表() {
            UserService userService = createUserService();
            Glossary g1 = new Glossary();
            g1.setId(1L);
            g1.setUserId(1L);
            g1.setSourceWord("hello");
            g1.setTargetWord("你好");
            g1.setRemark("greeting");

            Glossary g2 = new Glossary();
            g2.setId(2L);
            g2.setUserId(1L);
            g2.setSourceWord("world");
            g2.setTargetWord("世界");
            g2.setRemark("noun");

            when(glossaryMapper.selectList(any())).thenReturn(List.of(g1, g2));

            List<GlossaryResponse> result = userService.getGlossaryList(1L);

            assertEquals(2, result.size());
            assertEquals("hello", result.get(0).getSourceWord());
            assertEquals("世界", result.get(1).getTargetWord());
        }

        @Test
        void 空列表返回空数组() {
            UserService userService = createUserService();
            when(glossaryMapper.selectList(any())).thenReturn(List.of());

            List<GlossaryResponse> result = userService.getGlossaryList(1L);

            assertTrue(result.isEmpty());
        }

        @Test
        void getGlossaryTerms返回与列表相同() {
            UserService userService = createUserService();
            Glossary g = new Glossary();
            g.setId(1L);
            g.setUserId(1L);
            g.setSourceWord("test");
            g.setTargetWord("测试");
            when(glossaryMapper.selectList(any())).thenReturn(List.of(g));

            List<GlossaryResponse> result = userService.getGlossaryTerms(1L);

            assertEquals(1, result.size());
            assertEquals("test", result.get(0).getSourceWord());
        }
    }

    @Nested
    @DisplayName("术语库详情")
    class GlossaryDetailTests {

        @Test
        void 返回自己的术语详情() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("hello");
            glossary.setTargetWord("你好");
            glossary.setDeleted(0);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNotNull(result);
            assertEquals("hello", result.getSourceWord());
        }

        @Test
        void 术语不存在返回null() {
            UserService userService = createUserService();
            when(glossaryMapper.selectById(999L)).thenReturn(null);

            GlossaryResponse result = userService.getGlossaryDetail(1L, 999L);

            assertNull(result);
        }

        @Test
        void 术语已删除返回null() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNull(result);
        }

        @Test
        void 非自己的术语返回null() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            glossary.setDeleted(0);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("创建术语项")
    class CreateGlossaryItemTests {

        @Test
        void 创建成功返回术语响应() {
            UserService userService = createUserService();
            when(glossaryMapper.insert(any(Glossary.class))).thenAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(10L);
                return 1;
            });

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord("apple");
            req.setTargetWord("苹果");
            req.setRemark("fruit");

            GlossaryResponse result = userService.createGlossaryItem(1L, req);

            assertNotNull(result);
            assertEquals("apple", result.getSourceWord());
            assertEquals("苹果", result.getTargetWord());
            assertEquals("fruit", result.getRemark());
        }
    }

    @Nested
    @DisplayName("更新术语项")
    class UpdateGlossaryItemTests {

        @Test
        void 更新成功() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("old");
            glossary.setTargetWord("旧");
            glossary.setRemark("old remark");
            glossary.setDeleted(0);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);
            when(glossaryMapper.updateById(any(Glossary.class))).thenReturn(1);

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord("new");
            req.setTargetWord("新");

            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNotNull(result);
            // updateGlossaryItem modifies the object in place, so sourceWord IS updated
            assertEquals("new", result.getSourceWord());
            assertEquals("新", result.getTargetWord());
            assertEquals("old remark", result.getRemark());
        }

        @Test
        void 术语不存在返回null() {
            UserService userService = createUserService();
            when(glossaryMapper.selectById(999L)).thenReturn(null);

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord("new");
            GlossaryResponse result = userService.updateGlossaryItem(1L, 999L, req);

            assertNull(result);
        }

        @Test
        void 非自己的术语返回null() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            glossary.setDeleted(0);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            GlossaryItemRequest req = new GlossaryItemRequest();
            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNull(result);
        }

        @Test
        void 已删除术语返回null() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            GlossaryItemRequest req = new GlossaryItemRequest();
            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNull(result);
        }

        @Test
        void 只更新非空字段() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("original");
            glossary.setTargetWord("原始");
            glossary.setRemark("original remark");
            glossary.setDeleted(0);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);
            when(glossaryMapper.updateById(any())).thenReturn(1);

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setTargetWord("更新后的");
            req.setRemark("新备注");

            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNotNull(result);
            assertEquals("original", result.getSourceWord());
            assertEquals("更新后的", result.getTargetWord());
            assertEquals("新备注", result.getRemark());
        }
    }

    @Nested
    @DisplayName("删除术语项")
    class DeleteGlossaryItemTests {

        @Test
        void 术语不存在返回false() {
            UserService userService = createUserService();
            when(glossaryMapper.selectById(999L)).thenReturn(null);

            boolean result = userService.deleteGlossaryItem(1L, 999L);

            assertFalse(result);
        }

        @Test
        void 已删除术语返回false() {
            UserService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryMapper.selectById(1L)).thenReturn(glossary);

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("批量导入术语")
    class BatchImportGlossaryTests {

        @Test
        void 全部导入成功() {
            UserService userService = createUserService();
            when(glossaryMapper.insert(any(Glossary.class))).thenReturn(1);

            List<GlossaryItemRequest> items = List.of(
                buildItem("word1", "词1", "remark1"),
                buildItem("word2", "词2", "remark2"),
                buildItem("word3", "词3", "remark3")
            );

            int count = userService.batchImportGlossaryItems(1L, items);

            assertEquals(3, count);
        }

        @Test
        void 部分失败跳过() {
            UserService userService = createUserService();
            when(glossaryMapper.insert(any(Glossary.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("DB error"))
                .thenReturn(1);

            List<GlossaryItemRequest> items = List.of(
                buildItem("word1", "词1", null),
                buildItem("word2", "词2", null),
                buildItem("word3", "词3", null)
            );

            int count = userService.batchImportGlossaryItems(1L, items);

            assertEquals(2, count);
        }

        private GlossaryItemRequest buildItem(String source, String target, String remark) {
            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord(source);
            req.setTargetWord(target);
            req.setRemark(remark);
            return req;
        }
    }

    @Nested
    @DisplayName("用户偏好设置")
    class UserPreferencesDetailedTests {

        @Test
        void 更新已有偏好() {
            UserService userService = createUserService();
            UserPreference existing = new UserPreference();
            existing.setId(1L);
            existing.setUserId(1L);
            existing.setDefaultEngine("google");
            existing.setDefaultTargetLang("zh");
            existing.setEnableGlossary(true);
            existing.setEnableCache(true);
            existing.setAutoTranslateSelection(true);
            existing.setFontSize(14);
            existing.setThemeMode("light");
            when(userPreferenceMapper.findByUserId(1L)).thenReturn(existing);
            when(userPreferenceMapper.updateById(any())).thenReturn(1);

            UserPreferencesRequest req = new UserPreferencesRequest();
            req.setDefaultEngine("deepl");
            req.setFontSize(18);

            UserPreferencesResponse result = userService.updateUserPreferences(1L, req);

            assertNotNull(result);
            assertEquals("deepl", result.getDefaultEngine());
            assertEquals("zh", result.getDefaultTargetLang());
            assertEquals(18, result.getFontSize());
        }

        @Test
        void 创建新偏好使用默认值() {
            UserService userService = createUserService();
            when(userPreferenceMapper.findByUserId(1L)).thenReturn(null);
            when(userPreferenceMapper.insert(any())).thenReturn(1);

            UserPreferencesRequest req = new UserPreferencesRequest();
            req.setDefaultEngine(null);
            req.setDefaultTargetLang(null);

            UserPreferencesResponse result = userService.updateUserPreferences(1L, req);

            assertNotNull(result);
            assertEquals("google", result.getDefaultEngine());
            assertEquals("zh", result.getDefaultTargetLang());
            assertEquals(14, result.getFontSize());
        }
    }

    @Nested
    @DisplayName("用户配额")
    class UserQuotaDetailedTests {

        @Test
        void pro用户配额() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(quotaService.getMonthlyQuota("pro")).thenReturn(500000L);
            when(quotaService.getUsedThisMonth(1L)).thenReturn(100000L);
            when(limitProperties.getProConcurrencyLimit()).thenReturn(20);
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);

            var response = userService.getUserQuota(user);

            assertEquals("PRO", response.getUserLevel());
            assertEquals(500000L, response.getMonthlyChars());
            assertEquals(100000L, response.getUsedThisMonth());
            assertEquals(400000L, response.getRemainingChars());
            assertEquals(20, response.getConcurrencyLimit());
            assertEquals(800000L, response.getFastModeEquivalent());
        }

        @Test
        void 默认free等级() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setUserLevel(null);
            when(quotaService.getMonthlyQuota("free")).thenReturn(10000L);
            when(quotaService.getUsedThisMonth(1L)).thenReturn(0L);
            when(limitProperties.getFreeConcurrencyLimit()).thenReturn(5);
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);

            var response = userService.getUserQuota(user);

            assertEquals("FREE", response.getUserLevel());
            assertEquals(10000L, response.getRemainingChars());
            assertEquals(5, response.getConcurrencyLimit());
        }

        @Test
        void 已用完配额() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(quotaService.getMonthlyQuota("free")).thenReturn(10000L);
            when(quotaService.getUsedThisMonth(1L)).thenReturn(15000L);
            when(limitProperties.getFreeConcurrencyLimit()).thenReturn(5);
            when(limitProperties.getFastModeMultiplier()).thenReturn(0.5);
            when(limitProperties.getExpertModeMultiplier()).thenReturn(1.0);
            when(limitProperties.getTeamModeMultiplier()).thenReturn(2.0);

            var response = userService.getUserQuota(user);

            assertEquals(0L, response.getRemainingChars());
        }
    }

    @Nested
    @DisplayName("用户统计")
    class UserStatisticsDetailedTests {

        @Test
        void sumSourceTextLength为null时返回0() {
            UserService userService = createUserService();
            when(translationHistoryMapper.countByUserId(1L)).thenReturn(10);
            when(translationHistoryMapper.countByUserIdAndType(1L, "text")).thenReturn(8);
            when(translationHistoryMapper.countByUserIdAndType(1L, "document")).thenReturn(2);
            when(translationHistoryMapper.sumSourceTextLengthByUserId(1L)).thenReturn(null);
            when(translationHistoryMapper.countByUserIdAfter(anyLong(), any())).thenReturn(3);

            var response = userService.getUserStatistics(1L);

            assertEquals(0L, response.getTotalCharacters());
            assertEquals(10, response.getTotalTranslations());
        }
    }

    @Nested
    @DisplayName("更新用户信息")
    class UpdateUserTests {

        @Test
        void 调用mapper更新() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");

            userService.updateUser(user);

            verify(userMapper).updateById(user);
        }
    }
}
