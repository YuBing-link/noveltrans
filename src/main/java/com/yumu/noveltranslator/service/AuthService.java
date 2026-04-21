package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.util.EmailVerificationCodeUtil;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户认证服务：登录、注册、验证码、密码管理
 */
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final EmailVerificationCodeUtil emailVerificationCodeUtil;
    private final DeviceTokenService deviceTokenService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + email);
        }
        return new CustomUserDetails(user);
    }

    public Result<User> login(LoginRequest req) {
        if (req.getEmail() == null || !isValidEmail(req.getEmail().trim())) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(),
                              ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
        }
        if (req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR.getCode(),
                              "密码不能为空");
        }

        try {
            User user = userMapper.findByEmail(req.getEmail().trim());

            if (user != null) {
                if (PasswordUtil.verifyPassword(req.getPassword(), user.getPassword())) {
                    String token = jwtUtils.createToken((long) user.getId(), user.getEmail());

                    User userInfo = new User();
                    userInfo.setId(user.getId());
                    userInfo.setEmail(user.getEmail());
                    userInfo.setUsername(user.getUsername());
                    userInfo.setAvatar(user.getAvatar());
                    userInfo.setUserLevel(user.getUserLevel());
                    userInfo.setCreateTime(user.getCreateTime());

                    return Result.okWithToken(userInfo, token);
                } else {
                    return Result.error(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode(),
                                      ErrorCodeEnum.USER_PASSWORD_ERROR.getMessage());
                }
            } else {
                return Result.error(ErrorCodeEnum.USER_NOT_FOUND.getCode(),
                                  ErrorCodeEnum.USER_NOT_FOUND.getMessage());
            }
        } catch (Exception e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(),
                              "登录失败：" + e.getMessage());
        }
    }

    /**
     * 发送注册验证码
     */
    public Result sendVerificationCode(String email) {
        if (email == null || !isValidEmail(email.trim())) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(),
                              ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
        }

        User existingUser = userMapper.findByEmail(email.trim());
        if (existingUser != null) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode(),
                              ErrorCodeEnum.USER_EMAIL_EXISTS.getMessage());
        }

        boolean sent = emailVerificationCodeUtil.sendVerificationCode(email.trim());
        if (!sent) {
            Long lastSend = emailVerificationCodeUtil.getLastSendTime(email.trim());
            if (lastSend != null) {
                long elapsed = System.currentTimeMillis() - lastSend;
                if (elapsed < 60000) {
                    int remaining = (int) ((60000 - elapsed) / 1000) + 1;
                    return Result.error("429", "请等待 " + remaining + " 秒后再发送验证码");
                }
            }
            return Result.error(ErrorCodeEnum.EMAIL_SEND_FAILED.getCode(),
                              ErrorCodeEnum.EMAIL_SEND_FAILED.getMessage());
        }
        return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
    }

    /**
     * 发送重置密码验证码
     */
    public Result sendResetCode(String email) {
        if (email == null || !isValidEmail(email.trim())) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(),
                              ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
        }

        User existingUser = userMapper.findByEmail(email.trim());
        if (existingUser == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND.getCode(),
                              ErrorCodeEnum.USER_NOT_FOUND.getMessage());
        }

        boolean sent = emailVerificationCodeUtil.sendVerificationCode(email.trim());
        if (!sent) {
            Long lastSend = emailVerificationCodeUtil.getLastSendTime(email.trim());
            if (lastSend != null) {
                long elapsed = System.currentTimeMillis() - lastSend;
                if (elapsed < 60000) {
                    int remaining = (int) ((60000 - elapsed) / 1000) + 1;
                    return Result.error("429", "请等待 " + remaining + " 秒后再发送验证码");
                }
            }
            return Result.error(ErrorCodeEnum.EMAIL_SEND_FAILED.getCode(),
                              ErrorCodeEnum.EMAIL_SEND_FAILED.getMessage());
        }
        return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
    }

    @Transactional
    public Result<User> register(RegisterRequest req) {
        String email = req.getEmail();
        String code = req.getCode();
        String password = req.getPassword();

        if (email == null || !isValidEmail(email.trim())) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(),
                              ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
        }
        if (code == null || code.trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR.getCode(),
                              "验证码不能为空");
        }
        if (password == null || password.length() < 6) {
            return Result.error(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(),
                              ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getMessage());
        }

        if (!emailVerificationCodeUtil.verifyCode(email.trim(), code)) {
            return Result.error(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getCode(),
                              ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getMessage());
        }

        User existingUser = userMapper.findByEmail(email.trim());
        if (existingUser != null) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode(),
                              ErrorCodeEnum.USER_EMAIL_EXISTS.getMessage());
        }

        User newUser = new User();
        newUser.setEmail(email.trim());
        newUser.setPassword(PasswordUtil.hashPassword(password));
        newUser.setUserLevel("free");
        newUser.setUsername(req.getUsername());
        newUser.setAvatar(req.getAvatar());

        try {
            int result = userMapper.insert(newUser);
            if (result > 0) {
                User registeredUser = new User();
                registeredUser.setId(newUser.getId());
                registeredUser.setEmail(newUser.getEmail());
                registeredUser.setUsername(newUser.getUsername());
                registeredUser.setAvatar(newUser.getAvatar());
                registeredUser.setUserLevel(newUser.getUserLevel());
                registeredUser.setCreateTime(newUser.getCreateTime());

                return Result.ok(registeredUser, ErrorCodeEnum.SUCCESS.getCode());
            } else {
                return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(),
                                  "注册失败");
            }
        } catch (Exception e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(),
                              "注册过程中发生错误：" + e.getMessage());
        }
    }

    /**
     * 刷新令牌
     */
    public Result refreshToken(RefreshTokenRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR.getCode(), "刷新令牌不能为空");
        }

        try {
            var decoded = jwtUtils.verifyToken(request.getRefreshToken());
            Map<String, String> userInfo = jwtUtils.getUserInfoFromToken(request.getRefreshToken());

            String newToken = jwtUtils.createToken(
                    Long.parseLong(userInfo.get("userId")),
                    userInfo.get("email")
            );

            return Result.okWithToken(null, newToken);
        } catch (Exception e) {
            return Result.error(ErrorCodeEnum.TOKEN_INVALID.getCode(), "刷新令牌无效或已过期");
        }
    }

    /**
     * 修改密码
     */
    public Result changePassword(Long userId, ChangePasswordRequest request) {
        if (request.getOldPassword() == null || request.getNewPassword() == null) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR.getCode(), "密码不能为空");
        }

        if (request.getNewPassword().length() < 6) {
            return Result.error(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(),
                    ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getMessage());
        }

        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error(ErrorCodeEnum.USER_NOT_FOUND.getCode(), "用户不存在");
            }

            if (!PasswordUtil.verifyPassword(request.getOldPassword(), user.getPassword())) {
                return Result.error(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode(), "原密码错误");
            }

            user.setPassword(PasswordUtil.hashPassword(request.getNewPassword()));
            userMapper.updateById(user);

            return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
        } catch (Exception e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(), "修改密码失败：" + e.getMessage());
        }
    }

    /**
     * 重置密码
     */
    @Transactional
    public Result resetPassword(ResetPasswordRequest request) {
        if (request.getEmail() == null || !isValidEmail(request.getEmail().trim())) {
            return Result.error(ErrorCodeEnum.USER_EMAIL_INVALID.getCode(),
                    ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
        }

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR.getCode(), "验证码不能为空");
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            return Result.error(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode(),
                    ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getMessage());
        }

        if (!emailVerificationCodeUtil.verifyCode(request.getEmail().trim(), request.getCode())) {
            return Result.error(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getCode(),
                    ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getMessage());
        }

        try {
            User user = userMapper.findByEmail(request.getEmail().trim());
            if (user == null) {
                return Result.error(ErrorCodeEnum.USER_NOT_FOUND.getCode(), "用户不存在");
            }

            user.setPassword(PasswordUtil.hashPassword(request.getNewPassword()));
            userMapper.updateById(user);

            return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
        } catch (Exception e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(), "重置密码失败：" + e.getMessage());
        }
    }

    /**
     * 退出登录
     */
    @Transactional
    public Result logout(Long userId, String refreshToken) {
        if (refreshToken != null) {
            try {
                // 简化处理：仅清理 device token
                deviceTokenService.removeToken(refreshToken);
            } catch (Exception e) {
                // 忽略 token 解析失败
            }
        }
        return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
                           "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return Pattern.matches(emailRegex, email);
    }
}
