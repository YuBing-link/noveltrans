package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.auth.SendCodeRequest;
import com.yumu.noveltranslator.port.dto.auth.LoginRequest;
import com.yumu.noveltranslator.port.dto.auth.RegisterRequest;
import com.yumu.noveltranslator.port.dto.auth.ChangePasswordRequest;
import com.yumu.noveltranslator.port.dto.auth.ResetPasswordRequest;
import com.yumu.noveltranslator.port.dto.auth.RefreshTokenRequest;
import com.yumu.noveltranslator.port.dto.entity.UserStatisticsResponse;
import com.yumu.noveltranslator.port.dto.entity.UserQuotaResponse;
import com.yumu.noveltranslator.port.dto.entity.TranslationHistoryResponse;
import com.yumu.noveltranslator.port.dto.entity.UpdateUserProfileRequest;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesResponse;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesRequest;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
import com.yumu.noveltranslator.adapter.in.security.LoginRateLimiter;
import com.yumu.noveltranslator.port.in.AuthPort;
import com.yumu.noveltranslator.port.in.TranslationTaskPort;
import com.yumu.noveltranslator.port.in.UserPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Web 用户核心接口
 * 路径前缀: /user
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class WebUserController {

    private final AuthPort authPort;
    private final UserPort userPort;
    private final TranslationTaskPort translationTaskPort;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * 发送注册验证码
     * POST /user/send-code
     */
    @PostMapping("/send-code")
    public Result sendVerificationCode(@RequestBody @Valid SendCodeRequest request) {
        return authPort.sendVerificationCode(request.getEmail());
    }

    /**
     * 发送重置密码验证码
     * POST /user/send-reset-code
     */
    @PostMapping("/send-reset-code")
    public Result sendResetCode(@RequestBody @Valid SendCodeRequest request) {
        return authPort.sendResetCode(request.getEmail());
    }

    /**
     * 登录接口
     * POST /user/login
     */
    @PostMapping("/login")
    public Result<User> login(@RequestBody @Valid LoginRequest req, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!loginRateLimiter.allowLoginAttempt(clientIp)) {
            return Result.error(ErrorCodeEnum.RATE_LIMIT, "登录尝试次数过多，请稍后再试");
        }
        return authPort.login(req);
    }

    /**
     * 注册接口
     * POST /user/register
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody @Valid RegisterRequest req) {
        return authPort.register(req);
    }

    /**
     * 获取当前用户信息
     * GET /user/profile
     */
    @GetMapping("/profile")
    public Result<User> getCurrentUserProfile() {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();

        User userInfo = new User();
        userInfo.setId(userDetails.getId());
        userInfo.setEmail(userDetails.getEmail());
        userInfo.setUsername(userDetails.getUsername());
        userInfo.setAvatar(userDetails.getUser() != null ? userDetails.getUser().getAvatar() : null);
        userInfo.setUserLevel(userDetails.getUserLevel());
        userInfo.setCreateTime(userDetails.getUser() != null ? userDetails.getUser().getCreateTime() : null);

        return Result.ok(userInfo);
    }

    /**
     * 更新用户信息
     * PUT /user/profile
     */
    @PutMapping("/profile")
    public Result<User> updateUserProfile(@RequestBody @Valid UpdateUserProfileRequest request) {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();
        if (user == null) {
            user = new User();
            user.setId(userDetails.getId());
            user.setEmail(userDetails.getEmail());
            user.setUserLevel(userDetails.getUserLevel());
        }

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userPort.updateUser(user);

        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setEmail(user.getEmail());
        updatedUser.setUsername(user.getUsername());
        updatedUser.setAvatar(user.getAvatar());
        updatedUser.setUserLevel(user.getUserLevel());
        updatedUser.setCreateTime(user.getCreateTime());

        return Result.ok(updatedUser);
    }

    /**
     * 修改密码
     * POST /user/change-password
     */
    @PostMapping("/change-password")
    public Result changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        return authPort.changePassword(userId, request);
    }

    /**
     * 重置密码
     * POST /user/reset-password
     */
    @PostMapping("/reset-password")
    public Result resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return authPort.resetPassword(request);
    }

    /**
     * 刷新令牌
     * POST /user/refresh-token
     */
    @PostMapping("/refresh-token")
    public Result refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return authPort.refreshToken(request);
    }

    /**
     * 退出登录
     * POST /user/logout
     */
    @PostMapping("/logout")
    public Result logout(@RequestBody(required = false) Map<String, String> request,
                         @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = SecurityUtil.getRequiredUserId();
        String refreshToken = request != null ? request.get("refreshToken") : null;
        String jwt = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }
        return authPort.logout(userId, refreshToken, jwt);
    }

    /**
     * 获取统计数据
     * GET /user/statistics
     */
    @GetMapping("/statistics")
    public Result<UserStatisticsResponse> getStatistics() {
        Long userId = SecurityUtil.getRequiredUserId();
        UserStatisticsResponse statistics = userPort.getUserStatistics(userId);
        return Result.ok(statistics);
    }

    /**
     * 获取配额信息
     * GET /user/quota
     */
    @GetMapping("/quota")
    public Result<UserQuotaResponse> getQuota() {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();
        if (user == null) {
            user = new User();
            user.setId(userDetails.getId());
            user.setUserLevel(userDetails.getUserLevel());
        }
        UserQuotaResponse quota = userPort.getUserQuota(user);
        return Result.ok(quota);
    }

    /**
     * 获取翻译历史
     * GET /user/translation-history
     */
    @GetMapping("/translation-history")
    public Result<PageResponse<TranslationHistoryResponse>> getTranslationHistory(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "all") String type) {

        Long userId = SecurityUtil.getRequiredUserId();
        var histories = translationTaskPort.getTranslationHistory(userId, page, pageSize, type);
        int total = translationTaskPort.countTranslationHistory(userId, type);

        var responseList = histories.stream()
                .map(translationTaskPort::toHistoryResponse)
                .toList();

        PageResponse<TranslationHistoryResponse> response = PageResponse.of(page, pageSize, (long) total, responseList);
        return Result.ok(response);
    }

    /**
     * 获取用户偏好设置
     * GET /user/preferences
     */
    @GetMapping("/preferences")
    public Result<UserPreferencesResponse> getPreferences() {
        Long userId = SecurityUtil.getRequiredUserId();
        UserPreferencesResponse preferences = userPort.getUserPreferences(userId);
        return Result.ok(preferences);
    }

    /**
     * 更新用户偏好设置
     * PUT /user/preferences
     */
    @PutMapping("/preferences")
    public Result<UserPreferencesResponse> updatePreferences(@Valid @RequestBody UserPreferencesRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        UserPreferencesResponse preferences = userPort.updateUserPreferences(userId, request);
        return Result.ok(preferences);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
