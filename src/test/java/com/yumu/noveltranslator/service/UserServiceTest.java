package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.config.TranslationLimitProperties;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import com.yumu.noveltranslator.util.EmailVerificationCodeUtil;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private EmailVerificationCodeUtil emailVerificationCodeUtil;
    @Mock
    private TranslationHistoryMapper translationHistoryMapper;
    @Mock
    private GlossaryMapper glossaryMapper;
    @Mock
    private UserPreferenceMapper userPreferenceMapper;
    @Mock
    private com.yumu.noveltranslator.service.DeviceTokenService deviceTokenService;
    @Mock
    private TranslationLimitProperties limitProperties;
    @Mock
    private QuotaService quotaService;

    private UserService createUserService() {
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(service, "emailVerificationCodeUtil", emailVerificationCodeUtil);
        ReflectionTestUtils.setField(service, "translationHistoryMapper", translationHistoryMapper);
        ReflectionTestUtils.setField(service, "glossaryMapper", glossaryMapper);
        ReflectionTestUtils.setField(service, "userPreferenceMapper", userPreferenceMapper);
        ReflectionTestUtils.setField(service, "deviceTokenService", deviceTokenService);
        ReflectionTestUtils.setField(service, "limitProperties", limitProperties);
        ReflectionTestUtils.setField(service, "quotaService", quotaService);
        return service;
    }

    @Nested
    @DisplayName("登录")
    class LoginTests {

        @Test
        void 登录成功返回Token() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setUsername("testuser");
            user.setPassword("$2a$10$mockHash");
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);
            when(jwtUtils.createToken(1L, "test@test.com")).thenReturn("mock-jwt-token");

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");

            try (MockedStatic<PasswordUtil> mocked = mockStatic(PasswordUtil.class)) {
                mocked.when(() -> PasswordUtil.verifyPassword("password123", "$2a$10$mockHash")).thenReturn(true);
                Result<User> result = userService.login(req);

                assertTrue(result.isSuccess());
                assertEquals("mock-jwt-token", result.getToken());
                assertEquals("testuser", result.getData().getUsername());
            }
        }

        @Test
        void 密码错误返回失败() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword("$2a$10$mockHash");
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("wrong");

            try (MockedStatic<PasswordUtil> mocked = mockStatic(PasswordUtil.class)) {
                mocked.when(() -> PasswordUtil.verifyPassword("wrong", "$2a$10$mockHash")).thenReturn(false);
                Result<User> result = userService.login(req);
                assertFalse(result.isSuccess());
            }
        }

        @Test
        void 用户不存在返回失败() {
            UserService userService = createUserService();
            when(userMapper.findByEmail("notfound@test.com")).thenReturn(null);

            LoginRequest req = new LoginRequest();
            req.setEmail("notfound@test.com");
            req.setPassword("password");
            Result<User> result = userService.login(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 空邮箱返回失败() {
            UserService userService = createUserService();
            LoginRequest req = new LoginRequest();
            req.setEmail("");
            req.setPassword("password");
            Result<User> result = userService.login(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 无效邮箱格式返回失败() {
            UserService userService = createUserService();
            LoginRequest req = new LoginRequest();
            req.setEmail("invalid-email");
            req.setPassword("password");
            Result<User> result = userService.login(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 空密码返回失败() {
            UserService userService = createUserService();
            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("");
            Result<User> result = userService.login(req);

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("注册")
    class RegisterTests {

        @Test
        void 注册成功创建用户() {
            UserService userService = createUserService();
            when(userMapper.findByEmail("new@test.com")).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(emailVerificationCodeUtil.verifyCode("new@test.com", "123456")).thenReturn(true);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPassword("password123");
            req.setCode("123456");
            req.setUsername("newuser");

            try (MockedStatic<PasswordUtil> mocked = mockStatic(PasswordUtil.class)) {
                mocked.when(() -> PasswordUtil.hashPassword("password123")).thenReturn("$2a$10$hashed");
                Result<User> result = userService.register(req);

                assertTrue(result.isSuccess());
                assertEquals("newuser", result.getData().getUsername());
                assertEquals("new@test.com", result.getData().getEmail());
                verify(userMapper).insert(any(User.class));
            }
        }

        @Test
        void 验证码错误返回失败() {
            UserService userService = createUserService();
            when(emailVerificationCodeUtil.verifyCode("test@test.com", "wrong")).thenReturn(false);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");
            req.setCode("wrong");
            Result<User> result = userService.register(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 邮箱已存在返回失败() {
            UserService userService = createUserService();
            User existingUser = new User();
            existingUser.setEmail("exists@test.com");
            when(userMapper.findByEmail("exists@test.com")).thenReturn(existingUser);
            // verifyCode 在 findByEmail 之前调用，必须 mock
            when(emailVerificationCodeUtil.verifyCode("exists@test.com", "123456")).thenReturn(true);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("exists@test.com");
            req.setPassword("password123");
            req.setCode("123456");
            Result<User> result = userService.register(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 密码太短返回失败() {
            UserService userService = createUserService();
            RegisterRequest req = new RegisterRequest();
            req.setEmail("test@test.com");
            req.setPassword("123");
            req.setCode("123456");
            Result<User> result = userService.register(req);

            assertFalse(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("修改密码")
    class ChangePasswordTests {

        @Test
        void 旧密码正确成功修改() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setPassword("$2a$10$mockHash");
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("new123");

            try (MockedStatic<PasswordUtil> mocked = mockStatic(PasswordUtil.class)) {
                mocked.when(() -> PasswordUtil.verifyPassword("old123", "$2a$10$mockHash")).thenReturn(true);
                mocked.when(() -> PasswordUtil.hashPassword("new123")).thenReturn("$2a$10$newHash");

                Result result = userService.changePassword(1L, req);
                assertTrue(result.isSuccess());
                verify(userMapper).updateById(any(User.class));
            }
        }

        @Test
        void 旧密码错误返回失败() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setPassword("$2a$10$mockHash");
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("wrong");
            req.setNewPassword("new123");

            try (MockedStatic<PasswordUtil> mocked = mockStatic(PasswordUtil.class)) {
                mocked.when(() -> PasswordUtil.verifyPassword("wrong", "$2a$10$mockHash")).thenReturn(false);
                Result result = userService.changePassword(1L, req);
                assertFalse(result.isSuccess());
            }
        }

        @Test
        void 用户不存在返回失败() {
            UserService userService = createUserService();
            when(userMapper.selectById(999L)).thenReturn(null);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("new123");
            Result result = userService.changePassword(999L, req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 新密码太短返回失败() {
            UserService userService = createUserService();
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("123");
            Result result = userService.changePassword(1L, req);

            assertFalse(result.isSuccess());
        }
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
    @DisplayName("UserDetailsService")
    class UserDetailsServiceTests {

        @Test
        void 邮箱存在返回UserDetails() {
            UserService userService = createUserService();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword("$2a$10$hash");
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);

            var userDetails = userService.loadUserByUsername("test@test.com");

            assertNotNull(userDetails);
            assertEquals("test@test.com", userDetails.getUsername());
        }

        @Test
        void 邮箱不存在抛出异常() {
            UserService userService = createUserService();
            when(userMapper.findByEmail("notfound@test.com")).thenReturn(null);

            assertThrows(UsernameNotFoundException.class,
                    () -> userService.loadUserByUsername("notfound@test.com"));
        }
    }
}
