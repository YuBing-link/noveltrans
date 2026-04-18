package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.service.UserService;
import com.yumu.noveltranslator.util.SecurityUtil;
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

    private final UserService userService;
    private final TranslationTaskService translationTaskService;

    /**
     * 发送验证码接口
     * POST /user/send-code
     */
    @PostMapping("/send-code")
    public Result sendVerificationCode(@RequestBody @Valid SendCodeRequest request) {
        return userService.sendVerificationCode(request.getEmail());
    }

    /**
     * 登录接口
     * POST /user/login
     */
    @PostMapping("/login")
    public Result<User> login(@RequestBody @Valid LoginRequest req) {
        return userService.login(req);
    }

    /**
     * 注册接口
     * POST /user/register
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody @Valid RegisterRequest req) {
        return userService.register(req);
    }

    /**
     * 获取当前用户信息
     * GET /user/profile
     */
    @GetMapping("/profile")
    public Result<User> getCurrentUserProfile() {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();

        User userInfo = new User();
        userInfo.setId(user.getId());
        userInfo.setEmail(user.getEmail());
        userInfo.setUsername(user.getUsername());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setUserLevel(user.getUserLevel());
        userInfo.setCreateTime(user.getCreateTime());

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

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userService.updateUser(user);

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
        return userService.changePassword(userId, request);
    }

    /**
     * 重置密码
     * POST /user/reset-password
     */
    @PostMapping("/reset-password")
    public Result resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return userService.resetPassword(request);
    }

    /**
     * 刷新令牌
     * POST /user/refresh-token
     */
    @PostMapping("/refresh-token")
    public Result refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        return userService.refreshToken(request);
    }

    /**
     * 退出登录
     * POST /user/logout
     */
    @PostMapping("/logout")
    public Result logout(@RequestBody(required = false) Map<String, String> request) {
        Long userId = SecurityUtil.getRequiredUserId();
        String refreshToken = request != null ? request.get("refreshToken") : null;
        return userService.logout(userId, refreshToken);
    }

    /**
     * 获取统计数据
     * GET /user/statistics
     */
    @GetMapping("/statistics")
    public Result<UserStatisticsResponse> getStatistics() {
        Long userId = SecurityUtil.getRequiredUserId();
        UserStatisticsResponse statistics = userService.getUserStatistics(userId);
        return Result.ok(statistics);
    }

    /**
     * 获取配额信息
     * GET /user/quota
     */
    @GetMapping("/quota")
    public Result<UserQuotaResponse> getQuota() {
        User user = SecurityUtil.getRequiredUserDetails().getUser();
        UserQuotaResponse quota = userService.getUserQuota(user);
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
        var histories = translationTaskService.getTranslationHistory(userId, page, pageSize, type);
        int total = translationTaskService.countTranslationHistory(userId);

        var responseList = histories.stream()
                .map(translationTaskService::toHistoryResponse)
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
        UserPreferencesResponse preferences = userService.getUserPreferences(userId);
        return Result.ok(preferences);
    }

    /**
     * 更新用户偏好设置
     * PUT /user/preferences
     */
    @PutMapping("/preferences")
    public Result<UserPreferencesResponse> updatePreferences(@RequestBody UserPreferencesRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        UserPreferencesResponse preferences = userService.updateUserPreferences(userId, request);
        return Result.ok(preferences);
    }
}
