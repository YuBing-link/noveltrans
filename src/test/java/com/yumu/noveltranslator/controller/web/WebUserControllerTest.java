package com.yumu.noveltranslator.controller.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.AuthService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebUserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private AuthService authService;

    @org.mockito.Mock
    private UserService userService;

    @org.mockito.Mock
    private TranslationTaskService translationTaskService;

    private WebUserController controller;

    @BeforeEach
    void setUp() {
        controller = new WebUserController(authService, userService, translationTaskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void setupSecurityContext(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUsername("testuser");
        user.setUserLevel("free");
        return user;
    }

    @Nested
    @DisplayName("发送注册验证码")
    class SendVerificationCodeTests {

        @Test
        void 发送注册验证码成功() throws Exception {
            when(authService.sendVerificationCode("test@test.com"))
                .thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/send-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 邮箱格式错误返回400() throws Exception {
            mockMvc.perform(post("/user/send-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 邮箱为空返回400() throws Exception {
            mockMvc.perform(post("/user/send-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("发送重置密码验证码")
    class SendResetCodeTests {

        @Test
        void 发送重置验证码成功() throws Exception {
            when(authService.sendResetCode("test@test.com"))
                .thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/send-reset-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 邮箱为空返回400() throws Exception {
            mockMvc.perform(post("/user/send-reset-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("登录")
    class LoginTests {

        @Test
        void 登录成功() throws Exception {
            User user = createTestUser();
            when(authService.login(any(LoginRequest.class)))
                .thenReturn(Result.ok(user));

            mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
        }

        @Test
        void 邮箱为空返回400() throws Exception {
            mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 密码为空返回400() throws Exception {
            mockMvc.perform(post("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("注册")
    class RegisterTests {

        @Test
        void 注册成功() throws Exception {
            User user = createTestUser();
            when(authService.register(any(RegisterRequest.class)))
                .thenReturn(Result.ok(user));

            mockMvc.perform(post("/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\",\"password\":\"password123\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
        }

        @Test
        void 验证码为空返回400() throws Exception {
            mockMvc.perform(post("/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 密码长度不足返回400() throws Exception {
            mockMvc.perform(post("/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\",\"password\":\"123\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("获取用户信息")
    class ProfileTests {

        @Test
        void 获取当前用户信息成功() throws Exception {
            setupSecurityContext(createTestUser());

            mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.username").value("testuser"));
        }
    }

    @Nested
    @DisplayName("更新用户信息")
    class UpdateProfileTests {

        @Test
        void 更新用户信息成功() throws Exception {
            setupSecurityContext(createTestUser());
            doNothing().when(userService).updateUser(any());

            mockMvc.perform(put("/user/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"newname\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("修改密码")
    class ChangePasswordTests {

        @Test
        void 修改密码成功() throws Exception {
            setupSecurityContext(createTestUser());
            when(authService.changePassword(eq(1L), any(ChangePasswordRequest.class)))
                .thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"oldPassword\":\"old123\",\"newPassword\":\"new456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 旧密码为空返回400() throws Exception {
            setupSecurityContext(createTestUser());

            mockMvc.perform(post("/user/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"new456\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 新密码过短返回400() throws Exception {
            setupSecurityContext(createTestUser());

            mockMvc.perform(post("/user/change-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"oldPassword\":\"old123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("重置密码")
    class ResetPasswordTests {

        @Test
        void 重置密码成功() throws Exception {
            when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@test.com\",\"code\":\"123456\",\"newPassword\":\"new123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("刷新令牌")
    class RefreshTokenTests {

        @Test
        void 刷新令牌成功() throws Exception {
            User user = createTestUser();
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenReturn(Result.ok(user));

            mockMvc.perform(post("/user/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"some-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void refreshToken为空返回400() throws Exception {
            mockMvc.perform(post("/user/refresh-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("退出登录")
    class LogoutTests {

        @Test
        void 退出登录成功() throws Exception {
            setupSecurityContext(createTestUser());
            when(authService.logout(eq(1L), any())).thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"some-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 退出登录无请求体() throws Exception {
            setupSecurityContext(createTestUser());
            when(authService.logout(eq(1L), any())).thenReturn(Result.ok(null));

            mockMvc.perform(post("/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取统计数据")
    class StatisticsTests {

        @Test
        void 获取统计信息成功() throws Exception {
            setupSecurityContext(createTestUser());
            UserStatisticsResponse stats = new UserStatisticsResponse();
            when(userService.getUserStatistics(1L)).thenReturn(stats);

            mockMvc.perform(get("/user/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取配额信息")
    class QuotaTests {

        @Test
        void 获取配额信息成功() throws Exception {
            setupSecurityContext(createTestUser());
            UserQuotaResponse quota = new UserQuotaResponse();
            when(userService.getUserQuota(any(User.class))).thenReturn(quota);

            mockMvc.perform(get("/user/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取翻译历史")
    class TranslationHistoryTests {

        @Test
        void 获取翻译历史成功() throws Exception {
            setupSecurityContext(createTestUser());
            com.yumu.noveltranslator.entity.TranslationHistory history = new com.yumu.noveltranslator.entity.TranslationHistory();
            history.setTaskId("task-001");
            history.setUserId(1L);
            when(translationTaskService.getTranslationHistory(eq(1L), eq(1), eq(20), eq("all")))
                .thenReturn(List.of(history));
            when(translationTaskService.countTranslationHistory(1L)).thenReturn(1);
            when(translationTaskService.toHistoryResponse(any())).thenReturn(new TranslationHistoryResponse());

            mockMvc.perform(get("/user/translation-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        void 带分页参数获取历史() throws Exception {
            setupSecurityContext(createTestUser());
            when(translationTaskService.getTranslationHistory(eq(1L), eq(2), eq(10), eq("webpage")))
                .thenReturn(List.of());
            when(translationTaskService.countTranslationHistory(1L)).thenReturn(0);

            mockMvc.perform(get("/user/translation-history")
                    .param("page", "2")
                    .param("pageSize", "10")
                    .param("type", "webpage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取用户偏好设置")
    class PreferencesTests {

        @Test
        void 获取偏好设置成功() throws Exception {
            setupSecurityContext(createTestUser());
            UserPreferencesResponse prefs = new UserPreferencesResponse();
            when(userService.getUserPreferences(1L)).thenReturn(prefs);

            mockMvc.perform(get("/user/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 更新偏好设置成功() throws Exception {
            setupSecurityContext(createTestUser());
            UserPreferencesResponse prefs = new UserPreferencesResponse();
            when(userService.updateUserPreferences(eq(1L), any(UserPreferencesRequest.class))).thenReturn(prefs);

            mockMvc.perform(put("/user/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"defaultEngine\":\"google\",\"defaultTargetLang\":\"zh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
