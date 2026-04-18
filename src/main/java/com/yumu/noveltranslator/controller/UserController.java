package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import jakarta.validation.Valid;
import com.yumu.noveltranslator.service.DeviceTokenService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.service.UserService;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private TranslationTaskService translationTaskService;

    @Autowired
    private JwtUtils jwtUtils;

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
     * 需要认证
     */
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<User> getCurrentUserProfile() {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();

        // 创建一个新的 User 实例，去除密码字段
        User userInfo = new User();
        userInfo.setId(user.getId());
        userInfo.setEmail(user.getEmail());
        userInfo.setUsername(user.getUsername());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setUserLevel(user.getUserLevel());
        userInfo.setCreateTime(user.getCreateTime());

        return Result.ok(userInfo, "200");
    }

    /**
     * 更新用户信息
     * PUT /user/profile
     * 需要认证
     */
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<User> updateUserProfile(@RequestBody @Valid UpdateUserProfileRequest request) {
        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();

        // 更新用户信息
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userService.updateUser(user);

        // 返回更新后的用户信息（去除密码）
        User updatedUser = new User();
        updatedUser.setId(user.getId());
        updatedUser.setEmail(user.getEmail());
        updatedUser.setUsername(user.getUsername());
        updatedUser.setAvatar(user.getAvatar());
        updatedUser.setUserLevel(user.getUserLevel());
        updatedUser.setCreateTime(user.getCreateTime());

        return Result.ok(updatedUser, "200");
    }

    /**
     * 修改密码
     * POST /user/change-password
     * 需要认证
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
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
     * 需要认证
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public Result logout(@RequestBody(required = false) Map<String, String> request) {
        Long userId = SecurityUtil.getRequiredUserId();
        String refreshToken = request != null ? request.get("refreshToken") : null;
        return userService.logout(userId, refreshToken);
    }

    /**
     * 获取统计数据
     * GET /user/statistics
     * 需要认证
     */
    @GetMapping("/statistics")
    @PreAuthorize("isAuthenticated()")
    public Result<UserStatisticsResponse> getStatistics() {
        Long userId = SecurityUtil.getRequiredUserId();
        UserStatisticsResponse statistics = userService.getUserStatistics(userId);
        return Result.ok(statistics, "200");
    }

    /**
     * 获取配额信息
     * GET /user/quota
     * 需要认证
     */
    @GetMapping("/quota")
    @PreAuthorize("isAuthenticated()")
    public Result<UserQuotaResponse> getQuota() {
        User user = SecurityUtil.getRequiredUserDetails().getUser();
        UserQuotaResponse quota = userService.getUserQuota(user);
        return Result.ok(quota, "200");
    }

    /**
     * 获取翻译历史
     * GET /user/translation-history
     * 需要认证
     */
    @GetMapping("/translation-history")
    @PreAuthorize("isAuthenticated()")
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
        return Result.ok(response, "200");
    }

    /**
     * 网站登录后，注册设备 Token
     * POST /user/register-device
     */
    @PostMapping("/register-device")
    @PreAuthorize("isAuthenticated()")
    public Result<String> registerDevice(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");

        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error("设备 ID 不能为空", "400");
        }

        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();

        String token = jwtUtils.createToken((long) user.getId(), user.getEmail());
        deviceTokenService.registerToken(deviceId, token);

        return Result.ok("设备注册成功", "200");
    }

    /**
     * 插件获取 Token（通过设备 ID）
     * GET /user/get-token/{deviceId}
     */
    @GetMapping("/get-token/{deviceId}")
    public Result<Map<String, String>> getToken(@PathVariable String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error("设备 ID 不能为空", "400");
        }

        String token = deviceTokenService.getToken(deviceId);

        if (token == null) {
            return Result.error("未找到登录信息，请先在网站登录", "404");
        }

        try {
            var decoded = jwtUtils.verifyToken(token);
            Map<String, String> result = jwtUtils.getUserInfoFromToken(token);
            result.put("token", token);
            return Result.ok(result, "200");
        } catch (Exception e) {
            deviceTokenService.removeToken(deviceId);
            return Result.error("登录已过期，请重新登录", "401");
        }
    }

    // ==================== 术语库管理接口 ====================

    /**
     * 获取术语库列表
     * GET /user/glossaries
     * 需要认证
     */
    @GetMapping("/glossaries")
    @PreAuthorize("isAuthenticated()")
    public Result<List<GlossaryResponse>> getGlossaryList() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<GlossaryResponse> glossaries = userService.getGlossaryList(userId);
        return Result.ok(glossaries, "200");
    }

    /**
     * 获取术语库详情
     * GET /user/glossaries/{id}
     * 需要认证
     */
    @GetMapping("/glossaries/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<GlossaryResponse> getGlossaryDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error("术语库不存在", "404");
        }
        return Result.ok(glossary, "200");
    }

    /**
     * 创建术语项
     * POST /user/glossaries
     * 需要认证
     */
    @PostMapping("/glossaries")
    @PreAuthorize("isAuthenticated()")
    public Result<GlossaryResponse> createGlossaryItem(@RequestBody @Valid GlossaryItemRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.createGlossaryItem(userId, request);
        return Result.ok(glossary, "200");
    }

    /**
     * 更新术语项
     * PUT /user/glossaries/{id}
     * 需要认证
     */
    @PutMapping("/glossaries/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<GlossaryResponse> updateGlossaryItem(@PathVariable Long id, @RequestBody @Valid GlossaryItemRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.updateGlossaryItem(userId, id, request);
        if (glossary == null) {
            return Result.error("术语项不存在", "404");
        }
        return Result.ok(glossary, "200");
    }

    /**
     * 删除术语项
     * DELETE /user/glossaries/{id}
     * 需要认证
     */
    @DeleteMapping("/glossaries/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result deleteGlossaryItem(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        boolean success = userService.deleteGlossaryItem(userId, id);
        if (!success) {
            return Result.error("术语项不存在", "404");
        }
        return Result.ok(null, "200");
    }

    /**
     * 获取术语列表
     * GET /user/glossaries/{id}/terms
     * 需要认证
     * 注：当前设计中，此接口与 /user/glossaries 返回相同内容
     */
    @GetMapping("/glossaries/{id}/terms")
    @PreAuthorize("isAuthenticated()")
    public Result<List<GlossaryTermResponse>> getGlossaryTerms(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        // 验证术语库是否属于当前用户
        GlossaryResponse glossary = userService.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error("术语库不存在", "404");
        }
        List<GlossaryTermResponse> terms = userService.getGlossaryTerms(userId);
        return Result.ok(terms, "200");
    }

    // ==================== 用户偏好设置接口 ====================

    /**
     * 获取用户偏好设置
     * GET /user/preferences
     * 需要认证
     */
    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public Result<UserPreferencesResponse> getPreferences() {
        Long userId = SecurityUtil.getRequiredUserId();
        UserPreferencesResponse preferences = userService.getUserPreferences(userId);
        return Result.ok(preferences, "200");
    }

    /**
     * 更新用户偏好设置
     * PUT /user/preferences
     * 需要认证
     */
    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public Result<UserPreferencesResponse> updatePreferences(@RequestBody UserPreferencesRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        UserPreferencesResponse preferences = userService.updateUserPreferences(userId, request);
        return Result.ok(preferences, "200");
    }
}
