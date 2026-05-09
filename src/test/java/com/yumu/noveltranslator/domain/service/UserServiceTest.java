package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.domain.service.QuotaService;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesRequest;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesResponse;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.application.service.UserApplicationService;

import com.yumu.noveltranslator.port.dto.common.*;
import com.yumu.noveltranslator.port.dto.collab.*;
import com.yumu.noveltranslator.port.dto.entity.*;
import com.yumu.noveltranslator.port.dto.translation.*;
import com.yumu.noveltranslator.port.dto.subscription.*;
import com.yumu.noveltranslator.port.dto.auth.*;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPreference;
import com.yumu.noveltranslator.domain.model.Glossary;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.yumu.noveltranslator.util.PasswordUtil;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yumu.noveltranslator.port.dto.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepositoryPort userPort;
    @Mock private TranslationRepositoryPort translationPort;
    @Mock private GlossaryRepositoryPort glossaryPort;
    @Mock private TranslationLimitProperties limitProperties;
    @Mock private QuotaService quotaService;
    @Mock private com.yumu.noveltranslator.port.in.TranslationTaskPort translationTaskPort;

    private UserApplicationService createUserService() {
        return new UserApplicationService(userPort, translationPort, glossaryPort, limitProperties, quotaService, translationTaskPort);
    }

    @Nested
    @DisplayName("用户统计")
    class UserStatisticsTests {

        @Test
        void 返回完整统计数据() {
            UserApplicationService userService = createUserService();
            when(translationPort.countHistoryByUserId(1L)).thenReturn(50);
            when(translationPort.countHistoryByUserIdAndType(1L, "text")).thenReturn(30);
            when(translationPort.countHistoryByUserIdAndType(1L, "document")).thenReturn(20);
            when(translationPort.sumHistorySourceTextLengthByUserId(1L)).thenReturn(10000L);
            when(translationPort.countHistoryByUserIdAfter(anyLong(), any())).thenReturn(10);

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
            UserApplicationService userService = createUserService();
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
            UserApplicationService userService = createUserService();
            doAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(1L);
                return null;
            }).when(glossaryPort).saveGlossary(any(Glossary.class));

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
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));
            when(glossaryPort.deleteGlossary(any(Glossary.class))).thenReturn(true);

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertTrue(result);
        }

        @Test
        void 删除非自己的术语项失败() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("用户偏好")
    class UserPreferencesTests {

        @Test
        void 首次设置创建新偏好() {
            UserApplicationService userService = createUserService();
            when(userPort.findPreferenceByUserId(1L)).thenReturn(Optional.empty());
            doAnswer(invocation -> {
                UserPreference p = invocation.getArgument(0);
                p.setId(1L);
                return null;
            }).when(userPort).savePreference(any(UserPreference.class));

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
            UserApplicationService userService = createUserService();
            when(userPort.findPreferenceByUserId(1L)).thenReturn(Optional.empty());

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
            UserApplicationService userService = createUserService();
            when(userPort.countActiveUsers()).thenReturn(1000);
            when(translationPort.countActiveUsersAfter(any())).thenReturn(100);
            when(translationPort.countAllHistory()).thenReturn(50000L);
            when(translationPort.countHistoryAfter(any())).thenReturn(500L);
            when(translationPort.sumAllHistorySourceTextLength()).thenReturn(1000000L);
            when(translationPort.countDocumentTranslations()).thenReturn(200);
            when(glossaryPort.countAllGlossaries()).thenReturn(500);

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
        void 返回术语分页第一页() {
            UserApplicationService userService = createUserService();
            Glossary g1 = buildGlossary(1L, 1L, "hello", "你好", "greeting");
            Glossary g2 = buildGlossary(2L, 1L, "world", "世界", "noun");

            PageResult<Glossary> page = new PageResult<>(List.of(g1, g2), 2, 1, 20);
            when(glossaryPort.findGlossaryPaged(eq(1L), isNull(), eq(1), eq(20))).thenReturn(page);

            PageResponse<GlossaryResponse> result = userService.getGlossaryList(1L, 1, 20, null);

            assertEquals(2, result.getList().size());
            assertEquals(2, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals(20, result.getPageSize());
            assertEquals("hello", result.getList().get(0).getSourceWord());
            assertEquals("世界", result.getList().get(1).getTargetWord());
        }

        @Test
        void 分页第二页返回空() {
            UserApplicationService userService = createUserService();

            PageResult<Glossary> page = new PageResult<>(List.of(), 2, 2, 20);
            when(glossaryPort.findGlossaryPaged(eq(1L), isNull(), eq(2), eq(20))).thenReturn(page);

            PageResponse<GlossaryResponse> result = userService.getGlossaryList(1L, 2, 20, null);

            assertTrue(result.getList().isEmpty());
            assertEquals(2, result.getTotal());
            assertEquals(2, result.getPage());
        }

        @Test
        void 搜索过滤返回匹配结果() {
            UserApplicationService userService = createUserService();
            Glossary g = buildGlossary(1L, 1L, "hello", "你好", "greeting");

            PageResult<Glossary> page = new PageResult<>(List.of(g), 1, 1, 20);
            when(glossaryPort.findGlossaryPaged(eq(1L), eq("hello"), eq(1), eq(20))).thenReturn(page);

            PageResponse<GlossaryResponse> result = userService.getGlossaryList(1L, 1, 20, "hello");

            assertEquals(1, result.getList().size());
            assertEquals("hello", result.getList().get(0).getSourceWord());
        }

        @Test
        void 空列表返回空数组() {
            UserApplicationService userService = createUserService();

            PageResult<Glossary> page = new PageResult<>(List.of(), 0, 1, 20);
            when(glossaryPort.findGlossaryPaged(eq(1L), isNull(), eq(1), eq(20))).thenReturn(page);

            PageResponse<GlossaryResponse> result = userService.getGlossaryList(1L, 1, 20, null);

            assertTrue(result.getList().isEmpty());
            assertEquals(0, result.getTotal());
        }

        private Glossary buildGlossary(Long id, Long userId, String source, String target, String remark) {
            Glossary g = new Glossary();
            g.setId(id);
            g.setUserId(userId);
            g.setSourceWord(source);
            g.setTargetWord(target);
            g.setRemark(remark);
            return g;
        }
    }

    @Nested
    @DisplayName("术语库详情")
    class GlossaryDetailTests {

        @Test
        void 返回自己的术语详情() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("hello");
            glossary.setTargetWord("你好");
            glossary.setDeleted(0);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNotNull(result);
            assertEquals("hello", result.getSourceWord());
        }

        @Test
        void 术语不存在返回null() {
            UserApplicationService userService = createUserService();
            when(glossaryPort.findGlossaryById(999L)).thenReturn(Optional.empty());

            GlossaryResponse result = userService.getGlossaryDetail(1L, 999L);

            assertNull(result);
        }

        @Test
        void 术语已删除返回null() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNull(result);
        }

        @Test
        void 非自己的术语返回null() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            glossary.setDeleted(0);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            GlossaryResponse result = userService.getGlossaryDetail(1L, 1L);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("创建术语项")
    class CreateGlossaryItemTests {

        @Test
        void 创建成功返回术语响应() {
            UserApplicationService userService = createUserService();
            doAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(10L);
                return null;
            }).when(glossaryPort).saveGlossary(any(Glossary.class));

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
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("old");
            glossary.setTargetWord("旧");
            glossary.setRemark("old remark");
            glossary.setDeleted(0);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));
            doNothing().when(glossaryPort).updateGlossary(any(Glossary.class));

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
            UserApplicationService userService = createUserService();
            when(glossaryPort.findGlossaryById(999L)).thenReturn(Optional.empty());

            GlossaryItemRequest req = new GlossaryItemRequest();
            req.setSourceWord("new");
            GlossaryResponse result = userService.updateGlossaryItem(1L, 999L, req);

            assertNull(result);
        }

        @Test
        void 非自己的术语返回null() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(2L);
            glossary.setDeleted(0);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            GlossaryItemRequest req = new GlossaryItemRequest();
            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNull(result);
        }

        @Test
        void 已删除术语返回null() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            GlossaryItemRequest req = new GlossaryItemRequest();
            GlossaryResponse result = userService.updateGlossaryItem(1L, 1L, req);

            assertNull(result);
        }

        @Test
        void 只更新非空字段() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setSourceWord("original");
            glossary.setTargetWord("原始");
            glossary.setRemark("original remark");
            glossary.setDeleted(0);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));
            doNothing().when(glossaryPort).updateGlossary(any());

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
            UserApplicationService userService = createUserService();
            when(glossaryPort.findGlossaryById(999L)).thenReturn(Optional.empty());

            boolean result = userService.deleteGlossaryItem(1L, 999L);

            assertFalse(result);
        }

        @Test
        void 已删除术语返回false() {
            UserApplicationService userService = createUserService();
            Glossary glossary = new Glossary();
            glossary.setId(1L);
            glossary.setUserId(1L);
            glossary.setDeleted(1);
            when(glossaryPort.findGlossaryById(1L)).thenReturn(Optional.of(glossary));

            boolean result = userService.deleteGlossaryItem(1L, 1L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("批量导入术语")
    class BatchImportGlossaryTests {

        @Test
        void 全部导入成功() {
            UserApplicationService userService = createUserService();
            doAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(1L);
                return null;
            }).when(glossaryPort).saveGlossary(any(Glossary.class));

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
            UserApplicationService userService = createUserService();
            doAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(1L);
                return null;
            }).doThrow(new RuntimeException("DB error"))
              .doAnswer(invocation -> {
                Glossary g = invocation.getArgument(0);
                g.setId(1L);
                return null;
            }).when(glossaryPort).saveGlossary(any(Glossary.class));

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
            UserApplicationService userService = createUserService();
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
            when(userPort.findPreferenceByUserId(1L)).thenReturn(Optional.of(existing));
            doNothing().when(userPort).updatePreference(any());

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
            UserApplicationService userService = createUserService();
            when(userPort.findPreferenceByUserId(1L)).thenReturn(Optional.empty());
            doAnswer(invocation -> {
                UserPreference p = invocation.getArgument(0);
                p.setId(1L);
                return null;
            }).when(userPort).savePreference(any());

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
            UserApplicationService userService = createUserService();
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
            UserApplicationService userService = createUserService();
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
            UserApplicationService userService = createUserService();
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
            UserApplicationService userService = createUserService();
            when(translationPort.countHistoryByUserId(1L)).thenReturn(10);
            when(translationPort.countHistoryByUserIdAndType(1L, "text")).thenReturn(8);
            when(translationPort.countHistoryByUserIdAndType(1L, "document")).thenReturn(2);
            when(translationPort.sumHistorySourceTextLengthByUserId(1L)).thenReturn(null);
            when(translationPort.countHistoryByUserIdAfter(anyLong(), any())).thenReturn(3);

            var response = userService.getUserStatistics(1L);

            assertEquals(0L, response.getTotalCharacters());
            assertEquals(10, response.getTotalTranslations());
        }
    }

    @Nested
    @DisplayName("更新用户信息")
    class UpdateUserTests {

        @Test
        void 调用port更新() {
            UserApplicationService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");

            userService.updateUser(user);

            verify(userPort).update(user);
        }
    }
}
