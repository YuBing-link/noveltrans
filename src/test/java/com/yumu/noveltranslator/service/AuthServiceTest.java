package com.yumu.noveltranslator.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.mapper.TenantMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.util.EmailVerificationCodeUtil;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private EmailVerificationCodeUtil emailVerificationCodeUtil;

    @Mock
    private DeviceTokenService deviceTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, tenantMapper, jwtUtils, emailVerificationCodeUtil, deviceTokenService);
    }

    @Nested
    @DisplayName("加载用户")
    class LoadUserByUsernameTests {

        @Test
        void 找到用户返回UserDetails() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(PasswordUtil.hashPassword("password123"));
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);

            UserDetails userDetails = authService.loadUserByUsername("test@test.com");

            assertNotNull(userDetails);
            assertInstanceOf(CustomUserDetails.class, userDetails);
            assertEquals("test@test.com", userDetails.getUsername());
        }

        @Test
        void 用户不存在抛出异常() {
            when(userMapper.findByEmail("notfound@test.com")).thenReturn(null);

            assertThrows(UsernameNotFoundException.class, () ->
                    authService.loadUserByUsername("notfound@test.com"));
        }
    }

    @Nested
    @DisplayName("登录")
    class LoginTests {

        @Test
        void 登录成功返回用户和令牌() {
            String hashedPassword = PasswordUtil.hashPassword("password123");
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(hashedPassword);
            user.setUsername("testuser");
            user.setUserLevel("free");
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);
            when(jwtUtils.createToken(1L, "test@test.com")).thenReturn("jwt-token");

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");

            Result<User> result = authService.login(req);

            assertTrue(result.isSuccess());
            assertNotNull(result.getData());
            assertEquals("test@test.com", result.getData().getEmail());
            assertEquals("jwt-token", result.getToken());
        }

        @Test
        void 密码错误返回错误() {
            String hashedPassword = PasswordUtil.hashPassword("correctPassword");
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(hashedPassword);
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("wrongPassword");

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode(), result.getCode());
        }

        @Test
        void 用户不存在返回错误() {
            when(userMapper.findByEmail("notfound@test.com")).thenReturn(null);

            LoginRequest req = new LoginRequest();
            req.setEmail("notfound@test.com");
            req.setPassword("password123");

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), result.getCode());
        }

        @Test
        void null邮箱返回错误() {
            LoginRequest req = new LoginRequest();
            req.setEmail(null);
            req.setPassword("password123");

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(), result.getCode());
        }

        @Test
        void null密码返回错误() {
            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword(null);

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 异常被捕获返回错误() {
            when(userMapper.findByEmail("test@test.com")).thenThrow(new RuntimeException("DB error"));

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("登录失败"));
        }
    }

    @Nested
    @DisplayName("发送注册验证码")
    class SendVerificationCodeTests {

        @Test
        void 发送成功() {
            when(userMapper.findByEmail("new@test.com")).thenReturn(null);
            when(emailVerificationCodeUtil.sendVerificationCode("new@test.com")).thenReturn(true);

            Result result = authService.sendVerificationCode("new@test.com");

            assertTrue(result.isSuccess());
            verify(emailVerificationCodeUtil).sendVerificationCode("new@test.com");
        }

        @Test
        void 邮箱已存在返回错误() {
            User existingUser = new User();
            existingUser.setId(1L);
            existingUser.setEmail("exists@test.com");
            when(userMapper.findByEmail("exists@test.com")).thenReturn(existingUser);

            Result result = authService.sendVerificationCode("exists@test.com");

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode(), result.getCode());
        }

        @Test
        void 无效邮箱返回错误() {
            Result result = authService.sendVerificationCode("invalid-email");

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(), result.getCode());
        }

        @Test
        void 频率限制返回错误() {
            when(userMapper.findByEmail("test@test.com")).thenReturn(null);
            when(emailVerificationCodeUtil.sendVerificationCode("test@test.com")).thenReturn(false);
            long now = System.currentTimeMillis();
            when(emailVerificationCodeUtil.getLastSendTime("test@test.com")).thenReturn(now);

            Result result = authService.sendVerificationCode("test@test.com");

            assertFalse(result.isSuccess());
            assertEquals("429", result.getCode());
            assertTrue(result.getMessage().contains("请等待"));
        }
    }

    @Nested
    @DisplayName("发送重置密码验证码")
    class SendResetCodeTests {

        @Test
        void 发送成功() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);
            when(emailVerificationCodeUtil.sendVerificationCode("test@test.com")).thenReturn(true);

            Result result = authService.sendResetCode("test@test.com");

            assertTrue(result.isSuccess());
        }

        @Test
        void 用户不存在返回错误() {
            when(userMapper.findByEmail("notfound@test.com")).thenReturn(null);

            Result result = authService.sendResetCode("notfound@test.com");

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), result.getCode());
        }

        @Test
        void 无效邮箱返回错误() {
            Result result = authService.sendResetCode("not-an-email");

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(), result.getCode());
        }
    }

    @Nested
    @DisplayName("注册")
    class RegisterTests {

        @Test
        void 注册成功() {
            when(emailVerificationCodeUtil.verifyCode("new@test.com", "123456")).thenReturn(true);
            when(userMapper.findByEmail("new@test.com")).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(1);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPassword("password123");
            req.setCode("123456");
            req.setUsername("newuser");

            Result<User> result = authService.register(req);

            assertTrue(result.isSuccess());
            assertNotNull(result.getData());
            assertEquals("new@test.com", result.getData().getEmail());
            assertEquals("free", result.getData().getUserLevel());
        }

        @Test
        void 无效邮箱返回错误() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("invalid");
            req.setPassword("password123");
            req.setCode("123456");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(), result.getCode());
        }

        @Test
        void 空验证码返回错误() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");
            req.setCode("");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("验证码"));
        }

        @Test
        void 密码太短返回错误() {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("test@test.com");
            req.setPassword("123");
            req.setCode("123456");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(), result.getCode());
        }

        @Test
        void 验证码错误返回错误() {
            when(emailVerificationCodeUtil.verifyCode("test@test.com", "wrong")).thenReturn(false);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("test@test.com");
            req.setPassword("password123");
            req.setCode("wrong");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getCode(), result.getCode());
        }

        @Test
        void 邮箱已存在返回错误() {
            when(emailVerificationCodeUtil.verifyCode("exists@test.com", "123456")).thenReturn(true);
            User existingUser = new User();
            existingUser.setId(1L);
            existingUser.setEmail("exists@test.com");
            when(userMapper.findByEmail("exists@test.com")).thenReturn(existingUser);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("exists@test.com");
            req.setPassword("password123");
            req.setCode("123456");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode(), result.getCode());
        }

        @Test
        void 插入失败返回错误() {
            when(emailVerificationCodeUtil.verifyCode("new@test.com", "123456")).thenReturn(true);
            when(userMapper.findByEmail("new@test.com")).thenReturn(null);
            when(userMapper.insert(any(User.class))).thenReturn(0);

            RegisterRequest req = new RegisterRequest();
            req.setEmail("new@test.com");
            req.setPassword("password123");
            req.setCode("123456");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("注册失败"));
        }
    }

    @Nested
    @DisplayName("刷新令牌")
    class RefreshTokenTests {

        @Test
        void 成功返回新令牌() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("valid-refresh-token");
            DecodedJWT decodedJwt = mock(DecodedJWT.class);
            doReturn(decodedJwt).when(jwtUtils).verifyToken("valid-refresh-token");
            @SuppressWarnings("unchecked")
            Map<String, String> userInfo = Map.of("userId", "1", "email", "test@test.com");
            when(jwtUtils.getUserInfoFromToken("valid-refresh-token")).thenReturn(userInfo);
            when(jwtUtils.createToken(1L, "test@test.com")).thenReturn("new-jwt-token");

            Result result = authService.refreshToken(req);

            assertTrue(result.isSuccess());
            assertEquals("new-jwt-token", result.getToken());
        }

        @Test
        void null令牌返回错误() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken(null);

            Result result = authService.refreshToken(req);

            assertFalse(result.isSuccess());
        }

        @Test
        void 无效令牌返回错误() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("invalid-token");
            when(jwtUtils.verifyToken("invalid-token")).thenThrow(new RuntimeException("Invalid"));

            Result result = authService.refreshToken(req);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("无效"));
        }
    }

    @Nested
    @DisplayName("修改密码")
    class ChangePasswordTests {

        @Test
        void 修改成功() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(PasswordUtil.hashPassword("oldPassword"));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("oldPassword");
            req.setNewPassword("newPassword123");

            Result result = authService.changePassword(1L, req);

            assertTrue(result.isSuccess());
        }

        @Test
        void 用户不存在返回错误() {
            when(userMapper.selectById(1L)).thenReturn(null);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("oldPassword");
            req.setNewPassword("newPassword123");

            Result result = authService.changePassword(1L, req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_NOT_FOUND.getCode(), result.getCode());
        }

        @Test
        void 旧密码错误返回错误() {
            User user = new User();
            user.setId(1L);
            user.setPassword(PasswordUtil.hashPassword("correctOld"));
            when(userMapper.selectById(1L)).thenReturn(user);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("wrongOld");
            req.setNewPassword("newPassword123");

            Result result = authService.changePassword(1L, req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode(), result.getCode());
        }

        @Test
        void 新密码太短返回错误() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("oldPassword");
            req.setNewPassword("123");

            Result result = authService.changePassword(1L, req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(), result.getCode());
        }
    }

    @Nested
    @DisplayName("重置密码")
    class ResetPasswordTests {

        @Test
        void 重置成功() {
            when(emailVerificationCodeUtil.verifyCode("test@test.com", "123456")).thenReturn(true);
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(PasswordUtil.hashPassword("oldPassword"));
            when(userMapper.findByEmail("test@test.com")).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@test.com");
            req.setCode("123456");
            req.setNewPassword("newPassword123");

            Result result = authService.resetPassword(req);

            assertTrue(result.isSuccess());
        }

        @Test
        void 无效邮箱返回错误() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("invalid");
            req.setCode("123456");
            req.setNewPassword("newPassword123");

            Result result = authService.resetPassword(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(), result.getCode());
        }

        @Test
        void 空验证码返回错误() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@test.com");
            req.setCode("");
            req.setNewPassword("newPassword123");

            Result result = authService.resetPassword(req);

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("验证码"));
        }

        @Test
        void 密码太短返回错误() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@test.com");
            req.setCode("123456");
            req.setNewPassword("123");

            Result result = authService.resetPassword(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(), result.getCode());
        }

        @Test
        void 验证码错误返回错误() {
            when(emailVerificationCodeUtil.verifyCode("test@test.com", "wrong")).thenReturn(false);

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setEmail("test@test.com");
            req.setCode("wrong");
            req.setNewPassword("newPassword123");

            Result result = authService.resetPassword(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getCode(), result.getCode());
        }
    }

    @Nested
    @DisplayName("退出登录")
    class LogoutTests {

        @Test
        void 带刷新令牌成功() {
            doNothing().when(deviceTokenService).removeToken("refresh-token");

            Result result = authService.logout(1L, "refresh-token");

            assertTrue(result.isSuccess());
            verify(deviceTokenService).removeToken("refresh-token");
        }

        @Test
        void 不带刷新令牌成功() {
            Result result = authService.logout(1L, null);

            assertTrue(result.isSuccess());
            verify(deviceTokenService, never()).removeToken(anyString());
        }
    }
}
