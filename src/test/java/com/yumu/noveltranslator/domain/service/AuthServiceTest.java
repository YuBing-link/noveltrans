package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.port.dto.auth.ChangePasswordRequest;
import com.yumu.noveltranslator.port.dto.auth.ResetPasswordRequest;
import com.yumu.noveltranslator.port.dto.auth.RegisterRequest;
import com.yumu.noveltranslator.port.dto.auth.LoginRequest;
import com.yumu.noveltranslator.domain.service.AuthService;
import com.yumu.noveltranslator.port.dto.auth.RefreshTokenRequest;
import com.yumu.noveltranslator.port.dto.common.Result;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
import com.yumu.noveltranslator.port.out.EmailPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private EmailPort emailPort;

    @Mock
    private VerificationCodeService verificationCodeService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepositoryPort, jwtUtils, emailPort, verificationCodeService);
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
            when(userRepositoryPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));

            UserDetails userDetails = authService.loadUserByUsername("test@test.com");

            assertNotNull(userDetails);
            assertInstanceOf(CustomUserDetails.class, userDetails);
            assertEquals("test@test.com", userDetails.getUsername());
        }

        @Test
        void 用户不存在抛出异常() {
            when(userRepositoryPort.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

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
            when(userRepositoryPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
            when(jwtUtils.createToken(eq(1L), eq("test@test.com"), any())).thenReturn("jwt-token");

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
            when(userRepositoryPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));

            LoginRequest req = new LoginRequest();
            req.setEmail("test@test.com");
            req.setPassword("wrongPassword");

            Result<User> result = authService.login(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode(), result.getCode());
        }

        @Test
        void 用户不存在返回错误() {
            when(userRepositoryPort.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

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
            when(userRepositoryPort.findByEmail("test@test.com")).thenThrow(new RuntimeException("DB error"));

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
            when(userRepositoryPort.findByEmail("new@test.com")).thenReturn(Optional.empty());
            when(verificationCodeService.generateAndStore("new@test.com")).thenReturn("123456");
            doNothing().when(emailPort).sendVerificationCode(eq("new@test.com"), eq("123456"));

            Result result = authService.sendVerificationCode("new@test.com");

            assertTrue(result.isSuccess());
            verify(emailPort).sendVerificationCode("new@test.com", "123456");
        }

        @Test
        void 邮箱已存在返回错误() {
            User existingUser = new User();
            existingUser.setId(1L);
            existingUser.setEmail("exists@test.com");
            when(userRepositoryPort.findByEmail("exists@test.com")).thenReturn(Optional.of(existingUser));

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
    }

    @Nested
    @DisplayName("发送重置密码验证码")
    class SendResetCodeTests {

        @Test
        void 发送成功() {
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            when(userRepositoryPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
            when(verificationCodeService.generateAndStore("test@test.com")).thenReturn("123456");
            doNothing().when(emailPort).sendVerificationCode(eq("test@test.com"), eq("123456"));

            Result result = authService.sendResetCode("test@test.com");

            assertTrue(result.isSuccess());
        }

        @Test
        void 用户不存在返回错误() {
            when(userRepositoryPort.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

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
            when(verificationCodeService.verifyCode("new@test.com", "123456")).thenReturn(true);
            when(userRepositoryPort.findByEmail("new@test.com")).thenReturn(Optional.empty());
            doAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return null;
            }).when(userRepositoryPort).save(any(User.class));

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
            when(verificationCodeService.verifyCode("test@test.com", "wrong")).thenReturn(false);

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
            when(verificationCodeService.verifyCode("exists@test.com", "123456")).thenReturn(true);
            User existingUser = new User();
            existingUser.setId(1L);
            existingUser.setEmail("exists@test.com");
            when(userRepositoryPort.findByEmail("exists@test.com")).thenReturn(Optional.of(existingUser));

            RegisterRequest req = new RegisterRequest();
            req.setEmail("exists@test.com");
            req.setPassword("password123");
            req.setCode("123456");

            Result<User> result = authService.register(req);

            assertFalse(result.isSuccess());
            assertEquals(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode(), result.getCode());
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

            com.auth0.jwt.interfaces.Claim userIdClaim = mock(com.auth0.jwt.interfaces.Claim.class);
            com.auth0.jwt.interfaces.Claim emailClaim = mock(com.auth0.jwt.interfaces.Claim.class);
            com.auth0.jwt.interfaces.Claim tenantIdClaim = mock(com.auth0.jwt.interfaces.Claim.class);
            when(decodedJwt.getClaim("userId")).thenReturn(userIdClaim);
            when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
            when(decodedJwt.getClaim("tenantId")).thenReturn(tenantIdClaim);
            when(userIdClaim.asLong()).thenReturn(1L);
            when(emailClaim.asString()).thenReturn("test@test.com");
            when(tenantIdClaim.asLong()).thenReturn(null);

            @SuppressWarnings("unchecked")
            Map<String, String> userInfo = Map.of("userId", "1", "email", "test@test.com");
            when(jwtUtils.getUserInfoFromToken("valid-refresh-token")).thenReturn(userInfo);
            when(jwtUtils.createToken(eq(1L), eq("test@test.com"), any())).thenReturn("new-jwt-token");

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
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("oldPassword");
            req.setNewPassword("newPassword123");

            Result result = authService.changePassword(1L, req);

            assertTrue(result.isSuccess());
        }

        @Test
        void 用户不存在返回错误() {
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.empty());

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
            user.setPassword(PasswordUtil.hashPassword("correct0"));
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));

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
            when(verificationCodeService.verifyCode("test@test.com", "123456")).thenReturn(true);
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            user.setPassword(PasswordUtil.hashPassword("oldPassword"));
            when(userRepositoryPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));

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
            when(verificationCodeService.verifyCode("test@test.com", "wrong")).thenReturn(false);

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
            doNothing().when(userRepositoryPort).removeDeviceToken("refresh-token");

            Result result = authService.logout(1L, "refresh-token", "test-jwt");

            assertTrue(result.isSuccess());
            verify(userRepositoryPort).removeDeviceToken("refresh-token");
        }

        @Test
        void 不带刷新令牌成功() {
            Result result = authService.logout(1L, null, null);

            assertTrue(result.isSuccess());
            verify(userRepositoryPort, never()).removeDeviceToken(anyString());
        }
    }
}
