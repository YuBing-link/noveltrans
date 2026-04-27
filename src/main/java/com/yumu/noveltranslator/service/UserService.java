package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息服务：个人资料、术语库、偏好设置、统计数据
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final TranslationHistoryMapper translationHistoryMapper;
    private final GlossaryMapper glossaryMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final TranslationLimitProperties limitProperties;
    private final QuotaService quotaService;

    /**
     * 更新用户信息
     */
    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    /**
     * 获取用户统计数据
     */
    public UserStatisticsResponse getUserStatistics(Long userId) {
        UserStatisticsResponse response = new UserStatisticsResponse();

        int totalCount = translationHistoryMapper.countByUserId(userId);
        response.setTotalTranslations(totalCount);

        int textCount = translationHistoryMapper.countByUserIdAndType(userId, "text");
        int docCount = translationHistoryMapper.countByUserIdAndType(userId, "document");
        response.setTextTranslations(textCount);
        response.setDocumentTranslations(docCount);

        Long totalChars = translationHistoryMapper.sumSourceTextLengthByUserId(userId);
        response.setTotalCharacters(totalChars != null ? totalChars : 0L);
        response.setTotalDocuments(docCount);

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

        int concurrency;
        if ("max".equalsIgnoreCase(level)) {
            concurrency = limitProperties.getMaxConcurrencyLimit();
        } else if ("pro".equalsIgnoreCase(level)) {
            concurrency = limitProperties.getProConcurrencyLimit();
        } else {
            concurrency = limitProperties.getFreeConcurrencyLimit();
        }
        response.setConcurrencyLimit(concurrency);

        double fastMult = limitProperties.getFastModeMultiplier();
        double expertMult = limitProperties.getExpertModeMultiplier();
        double teamMult = limitProperties.getTeamModeMultiplier();
        response.setFastModeEquivalent((long) (remaining / fastMult));
        response.setExpertModeEquivalent((long) (remaining / expertMult));
        response.setTeamModeEquivalent((long) (remaining / teamMult));

        return response;
    }

    /**
     * 获取术语库列表
     */
    public PageResponse<GlossaryResponse> getGlossaryList(Long userId, int page, int pageSize, String search) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId);
        if (search != null && !search.isEmpty()) {
            wrapper.and(w -> w.like(Glossary::getSourceWord, search)
                              .or()
                              .like(Glossary::getTargetWord, search));
        }
        wrapper.orderByDesc(Glossary::getCreateTime);

        Page<Glossary> pageParam = new Page<>(page, pageSize);
        Page<Glossary> resultPage = glossaryMapper.selectPage(pageParam, wrapper);

        List<GlossaryResponse> list = resultPage.getRecords().stream()
                .map(this::toGlossaryResponse)
                .collect(Collectors.toList());

        return PageResponse.of(page, pageSize, resultPage.getTotal(), list);
    }

    /**
     * 获取术语库详情
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
     * 获取术语列表
     */
    public List<GlossaryResponse> getGlossaryTerms(Long userId) {
        return fetchAllGlossaries(userId);
    }

    private List<GlossaryResponse> fetchAllGlossaries(Long userId) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Glossary::getUserId, userId)
               .orderByDesc(Glossary::getCreateTime);
        return glossaryMapper.selectList(wrapper).stream()
                .map(this::toGlossaryResponse)
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
            return buildDefaultPreferences();
        }
        return toPreferencesResponse(pref);
    }

    /**
     * 更新用户偏好设置
     */
    public UserPreferencesResponse updateUserPreferences(Long userId, UserPreferencesRequest request) {
        UserPreference pref = userPreferenceMapper.findByUserId(userId);
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(userId);
            pref.setDefaultEngine(request.getDefaultEngine() != null ? request.getDefaultEngine() : "google");
            pref.setDefaultTargetLang(request.getDefaultTargetLang() != null ? request.getDefaultTargetLang() : "zh");
            pref.setEnableGlossary(request.getEnableGlossary() != null ? request.getEnableGlossary() : true);
            pref.setDefaultGlossaryId(request.getDefaultGlossaryId());
            pref.setEnableCache(request.getEnableCache() != null ? request.getEnableCache() : true);
            pref.setAutoTranslateSelection(request.getAutoTranslateSelection() != null ? request.getAutoTranslateSelection() : true);
            pref.setFontSize(request.getFontSize() != null ? request.getFontSize() : 14);
            pref.setThemeMode(request.getThemeMode() != null ? request.getThemeMode() : "light");
            userPreferenceMapper.insert(pref);
        } else {
            if (request.getDefaultEngine() != null) pref.setDefaultEngine(request.getDefaultEngine());
            if (request.getDefaultTargetLang() != null) pref.setDefaultTargetLang(request.getDefaultTargetLang());
            if (request.getEnableGlossary() != null) pref.setEnableGlossary(request.getEnableGlossary());
            if (request.getDefaultGlossaryId() != null) pref.setDefaultGlossaryId(request.getDefaultGlossaryId());
            if (request.getEnableCache() != null) pref.setEnableCache(request.getEnableCache());
            if (request.getAutoTranslateSelection() != null) pref.setAutoTranslateSelection(request.getAutoTranslateSelection());
            if (request.getFontSize() != null) pref.setFontSize(request.getFontSize());
            if (request.getThemeMode() != null) pref.setThemeMode(request.getThemeMode());
            userPreferenceMapper.updateById(pref);
        }
        return toPreferencesResponse(pref);
    }

    /**
     * 获取平台统计信息（跨租户全局统计）
     */
    public PlatformStatsResponse getPlatformStats() {
        PlatformStatsResponse response = new PlatformStatsResponse();

        try {
            TenantContext.setBypassTenant(true);

            response.setTotalUsers(userMapper.countActiveUsers());

            LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
            LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            response.setActiveUsersWeek(translationHistoryMapper.countActiveUsersAfter(weekAgo));
            response.setActiveUsersMonth(translationHistoryMapper.countActiveUsersAfter(monthAgo));
            response.setActiveUsersToday(translationHistoryMapper.countActiveUsersAfter(todayStart));

            response.setTotalTranslations(translationHistoryMapper.countAll());
            response.setTranslationsToday(translationHistoryMapper.countAfter(todayStart));
            response.setTotalCharacters(translationHistoryMapper.sumAllSourceTextLength());
            response.setTotalDocumentTranslations(translationHistoryMapper.countDocumentTranslations());
            response.setTotalGlossaries(glossaryMapper.selectCount(new LambdaQueryWrapper<>()).intValue());

            response.setSystemStatus("normal");
        } finally {
            TenantContext.setBypassTenant(false);
        }

        return response;
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
        response.setDefaultGlossaryId(pref.getDefaultGlossaryId());
        response.setEnableCache(pref.getEnableCache());
        response.setAutoTranslateSelection(pref.getAutoTranslateSelection());
        response.setFontSize(pref.getFontSize());
        response.setThemeMode(pref.getThemeMode());
        return response;
    }

    private GlossaryResponse toGlossaryResponse(Glossary glossary) {
        GlossaryResponse response = new GlossaryResponse();
        response.setId(glossary.getId());
        response.setSourceWord(glossary.getSourceWord());
        response.setTargetWord(glossary.getTargetWord());
        response.setRemark(glossary.getRemark());
        response.setCreateTime(glossary.getCreateTime());
        return response;
    }
}
