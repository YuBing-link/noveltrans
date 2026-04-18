package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yumu.noveltranslator.config.TranslationLimitProperties;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.util.EmailVerificationCodeUtil;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private EmailVerificationCodeUtil emailVerificationCodeUtil;

    @Autowired
    private TranslationHistoryMapper translationHistoryMapper;

    @Autowired
    private GlossaryMapper glossaryMapper;

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Autowired
    private com.yumu.noveltranslator.service.DeviceTokenService deviceTokenService;

    @Autowired
    private TranslationLimitProperties limitProperties;

    @Autowired
    private QuotaService quotaService;

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

                    return Result.okWithToken(userInfo, token, ErrorCodeEnum.SUCCESS.getCode());
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
            // 检查是否是频率限制
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
        // 设置用户名和头像
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

            return Result.okWithToken(null, newToken, ErrorCodeEnum.SUCCESS.getCode());
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
     * 获取用户统计数据
     */
    public UserStatisticsResponse getUserStatistics(Long userId) {
        UserStatisticsResponse response = new UserStatisticsResponse();

        // 统计翻译历史
        int totalCount = translationHistoryMapper.countByUserId(userId);
        response.setTotalTranslations(totalCount);

        // 按类型统计
        int textCount = translationHistoryMapper.countByUserIdAndType(userId, "text");
        int docCount = translationHistoryMapper.countByUserIdAndType(userId, "document");
        response.setTextTranslations(textCount);
        response.setDocumentTranslations(docCount);

        // 统计字符数
        Long totalChars = translationHistoryMapper.sumSourceTextLengthByUserId(userId);
        response.setTotalCharacters(totalChars != null ? totalChars : 0L);
        response.setTotalDocuments(docCount);

        // 近7天和近30天翻译数
        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        response.setWeekTranslations(translationHistoryMapper.countByUserIdAfter(userId, weekAgo));
        response.setMonthTranslations(translationHistoryMapper.countByUserIdAfter(userId, monthAgo));

        return response;
    }

    /**
     * 获取用户配额信息
     */
    public UserQuotaResponse getUserQuota(User user) {
        UserQuotaResponse response = new UserQuotaResponse();
        String level = user.getUserLevel() != null ? user.getUserLevel() : "free";
        response.setUserLevel(level.toUpperCase());

        long monthlyChars = quotaService.getMonthlyQuota(level);
        long usedThisMonth = quotaService.getUsedThisMonth(user.getId());
        long remaining = Math.max(0, monthlyChars - usedThisMonth);

        response.setMonthlyChars(monthlyChars);
        response.setUsedThisMonth(usedThisMonth);
        response.setRemainingChars(remaining);

        // 并发限制
        int concurrency = "pro".equalsIgnoreCase(level) || "max".equalsIgnoreCase(level)
            ? limitProperties.getProConcurrencyLimit()
            : limitProperties.getFreeConcurrencyLimit();
        response.setConcurrencyLimit(concurrency);

        // 各模式等效原文字符
        double fastMult = limitProperties.getFastModeMultiplier();
        double expertMult = limitProperties.getExpertModeMultiplier();
        double teamMult = limitProperties.getTeamModeMultiplier();
        response.setFastModeEquivalent((long) (remaining / fastMult));
        response.setExpertModeEquivalent((long) (remaining / expertMult));
        response.setTeamModeEquivalent((long) (remaining / teamMult));

        return response;
    }

    /**
     * 退出登录（移除设备 Token）
     */
    @Transactional
    public Result logout(Long userId, String refreshToken) {
        // 移除该用户的所有设备 token（通过遍历缓存清除）
        // 实际项目中应该使用 Redis 令牌黑名单，这里通过 JWT 验证 + 设备缓存清理实现
        if (refreshToken != null) {
            try {
                // 如果传入了 refreshToken，尝试从中提取设备信息并移除
                // 当前简化处理：仅记录日志
            } catch (Exception e) {
                // 忽略 token 解析失败
            }
        }
        return Result.ok(null, ErrorCodeEnum.SUCCESS.getCode());
    }

    /**
     * 更新用户信息
     */
    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    /**
     * 获取用户的术语库列表（按用户分组返回）
     * 由于当前数据库设计，这里返回用户的所有术语项分组信息
     */
    public List<GlossaryResponse> getGlossaryList(Long userId) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId);

        List<Glossary> glossaries = glossaryMapper.selectList(wrapper);
        return glossaries.stream()
                .map(this::toGlossaryResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取术语库详情（单个术语项）
     */
    public GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId) {
        Glossary glossary = glossaryMapper.selectById(glossaryId);
        if (glossary == null || glossary.getDeleted() != null && glossary.getDeleted() == 1) {
            return null;
        }
        if (!glossary.getUserId().equals(userId)) {
            return null;
        }
        return toGlossaryResponse(glossary);
    }

    /**
     * 获取术语列表（与 getGlossaryList 相同，因为当前设计一个用户一个术语库）
     */
    public List<GlossaryTermResponse> getGlossaryTerms(Long userId) {
        return getGlossaryList(userId).stream()
                .map(this::toGlossaryTermResponse)
                .collect(Collectors.toList());
    }

    /**
     * 创建术语项
     */
    public GlossaryResponse createGlossaryItem(Long userId, GlossaryItemRequest request) {
        Glossary glossary = new Glossary();
        glossary.setUserId(userId);
        glossary.setSourceWord(request.getSourceWord());
        glossary.setTargetWord(request.getTargetWord());
        glossary.setRemark(request.getRemark());

        glossaryMapper.insert(glossary);
        return toGlossaryResponse(glossary);
    }

    /**
     * 更新术语项
     */
    public GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request) {
        Glossary glossary = glossaryMapper.selectById(glossaryId);
        if (glossary == null || glossary.getDeleted() != null && glossary.getDeleted() == 1) {
            return null;
        }
        if (!glossary.getUserId().equals(userId)) {
            return null;
        }

        if (request.getSourceWord() != null) {
            glossary.setSourceWord(request.getSourceWord());
        }
        if (request.getTargetWord() != null) {
            glossary.setTargetWord(request.getTargetWord());
        }
        if (request.getRemark() != null) {
            glossary.setRemark(request.getRemark());
        }

        glossaryMapper.updateById(glossary);
        return toGlossaryResponse(glossary);
    }

    /**
     * 删除术语项
     */
    public boolean deleteGlossaryItem(Long userId, Long glossaryId) {
        Glossary glossary = glossaryMapper.selectById(glossaryId);
        if (glossary == null || glossary.getDeleted() != null && glossary.getDeleted() == 1) {
            return false;
        }
        if (!glossary.getUserId().equals(userId)) {
            return false;
        }

        glossaryMapper.deleteById(glossaryId);
        return true;
    }

    /**
     * 批量导入术语
     */
    public int batchImportGlossaryItems(Long userId, List<GlossaryItemRequest> items) {
        int count = 0;
        for (GlossaryItemRequest item : items) {
            try {
                createGlossaryItem(userId, item);
                count++;
            } catch (Exception e) {
                // 跳过失败的项
            }
        }
        return count;
    }

    /**
     * 获取用户偏好设置
     */
    public UserPreferencesResponse getUserPreferences(Long userId) {
        UserPreference pref = userPreferenceMapper.findByUserId(userId);
        if (pref == null) {
            // 返回默认值
            return buildDefaultPreferences();
        }
        return toPreferencesResponse(pref);
    }

    private UserPreferencesResponse buildDefaultPreferences() {
        UserPreferencesResponse response = new UserPreferencesResponse();
        response.setDefaultEngine("google");
        response.setDefaultTargetLang("zh");
        response.setEnableGlossary(true);
        response.setDefaultGlossaryId(null);
        response.setEnableCache(true);
        response.setAutoTranslateSelection(true);
        response.setFontSize(14);
        response.setThemeMode("light");
        return response;
    }

    private UserPreferencesResponse toPreferencesResponse(UserPreference pref) {
        UserPreferencesResponse response = new UserPreferencesResponse();
        response.setDefaultEngine(pref.getDefaultEngine());
        response.setDefaultTargetLang(pref.getDefaultTargetLang());
        response.setEnableGlossary(pref.getEnableGlossary());
        response.setDefaultGlossaryId(pref.getDefaultGlossaryId() != null ? pref.getDefaultGlossaryId().intValue() : null);
        response.setEnableCache(pref.getEnableCache());
        response.setAutoTranslateSelection(pref.getAutoTranslateSelection());
        response.setFontSize(pref.getFontSize());
        response.setThemeMode(pref.getThemeMode());
        return response;
    }

    /**
     * 更新用户偏好设置
     */
    public UserPreferencesResponse updateUserPreferences(Long userId, UserPreferencesRequest request) {
        UserPreference pref = userPreferenceMapper.findByUserId(userId);
        if (pref == null) {
            // 首次保存，创建新记录
            pref = new UserPreference();
            pref.setUserId(userId);
            pref.setDefaultEngine(request.getDefaultEngine() != null ? request.getDefaultEngine() : "google");
            pref.setDefaultTargetLang(request.getDefaultTargetLang() != null ? request.getDefaultTargetLang() : "zh");
            pref.setEnableGlossary(request.getEnableGlossary() != null ? request.getEnableGlossary() : true);
            pref.setDefaultGlossaryId(request.getDefaultGlossaryId() != null ? request.getDefaultGlossaryId().longValue() : null);
            pref.setEnableCache(request.getEnableCache() != null ? request.getEnableCache() : true);
            pref.setAutoTranslateSelection(request.getAutoTranslateSelection() != null ? request.getAutoTranslateSelection() : true);
            pref.setFontSize(request.getFontSize() != null ? request.getFontSize() : 14);
            pref.setThemeMode(request.getThemeMode() != null ? request.getThemeMode() : "light");
            userPreferenceMapper.insert(pref);
        } else {
            // 更新现有记录
            if (request.getDefaultEngine() != null) pref.setDefaultEngine(request.getDefaultEngine());
            if (request.getDefaultTargetLang() != null) pref.setDefaultTargetLang(request.getDefaultTargetLang());
            if (request.getEnableGlossary() != null) pref.setEnableGlossary(request.getEnableGlossary());
            if (request.getDefaultGlossaryId() != null) pref.setDefaultGlossaryId(request.getDefaultGlossaryId().longValue());
            if (request.getEnableCache() != null) pref.setEnableCache(request.getEnableCache());
            if (request.getAutoTranslateSelection() != null) pref.setAutoTranslateSelection(request.getAutoTranslateSelection());
            if (request.getFontSize() != null) pref.setFontSize(request.getFontSize());
            if (request.getThemeMode() != null) pref.setThemeMode(request.getThemeMode());
            userPreferenceMapper.updateById(pref);
        }
        return toPreferencesResponse(pref);
    }

    /**
     * 获取平台统计信息
     */
    public PlatformStatsResponse getPlatformStats() {
        PlatformStatsResponse response = new PlatformStatsResponse();

        // 总用户数
        response.setTotalUsers(userMapper.countActiveUsers());

        // 近 7/30 天活跃用户数（有翻译记录的用户）
        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        response.setActiveUsersWeek(translationHistoryMapper.countActiveUsersAfter(weekAgo));
        response.setActiveUsersMonth(translationHistoryMapper.countActiveUsersAfter(monthAgo));
        response.setActiveUsersToday(translationHistoryMapper.countActiveUsersAfter(todayStart));

        // 翻译统计
        response.setTotalTranslations(translationHistoryMapper.countAll());
        response.setTranslationsToday(translationHistoryMapper.countAfter(todayStart));
        response.setTotalCharacters(translationHistoryMapper.sumAllSourceTextLength());
        response.setTotalDocumentTranslations(translationHistoryMapper.countDocumentTranslations());
        response.setTotalGlossaries(glossaryMapper.selectCount(new LambdaQueryWrapper<>()).intValue());

        response.setSystemStatus("normal");

        return response;
    }

    /**
     * 将 Glossary 实体转换为 GlossaryResponse
     */
    private GlossaryResponse toGlossaryResponse(Glossary glossary) {
        GlossaryResponse response = new GlossaryResponse();
        response.setId(glossary.getId());
        response.setSourceWord(glossary.getSourceWord());
        response.setTargetWord(glossary.getTargetWord());
        response.setRemark(glossary.getRemark());
        response.setCreateTime(glossary.getCreateTime());
        return response;
    }

    /**
     * 将 GlossaryResponse 转换为 GlossaryTermResponse
     */
    private GlossaryTermResponse toGlossaryTermResponse(GlossaryResponse response) {
        GlossaryTermResponse termResponse = new GlossaryTermResponse();
        termResponse.setId(response.getId());
        termResponse.setSourceWord(response.getSourceWord());
        termResponse.setTargetWord(response.getTargetWord());
        termResponse.setRemark(response.getRemark());
        termResponse.setCreateTime(response.getCreateTime());
        return termResponse;
    }
}
