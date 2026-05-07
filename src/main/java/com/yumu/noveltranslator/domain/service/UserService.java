package com.yumu.noveltranslator.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.dto.entity.UserStatisticsResponse;
import com.yumu.noveltranslator.dto.entity.UserQuotaResponse;
import com.yumu.noveltranslator.dto.entity.UserPreferencesResponse;
import com.yumu.noveltranslator.dto.entity.UserPreferencesRequest;
import com.yumu.noveltranslator.dto.entity.PlatformStatsResponse;
import com.yumu.noveltranslator.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPreference;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
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

    private final UserRepositoryPort userPort;
    private final TranslationRepositoryPort translationPort;
    private final GlossaryRepositoryPort glossaryPort;
    private final TranslationLimitProperties limitProperties;
    private final QuotaService quotaService;

    /**
     * 更新用户信息
     */
    public void updateUser(User user) {
        userPort.update(user);
    }

    /**
     * 获取用户统计数据
     */
    public UserStatisticsResponse getUserStatistics(Long userId) {
        UserStatisticsResponse response = new UserStatisticsResponse();

        int totalCount = translationPort.countHistoryByUserId(userId);
        response.setTotalTranslations(totalCount);

        int textCount = translationPort.countHistoryByUserIdAndType(userId, "text");
        int docCount = translationPort.countHistoryByUserIdAndType(userId, "document");
        response.setTextTranslations(textCount);
        response.setDocumentTranslations(docCount);

        Long totalChars = translationPort.sumHistorySourceTextLengthByUserId(userId);
        response.setTotalCharacters(totalChars != null ? totalChars : 0L);
        response.setTotalDocuments(docCount);

        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
        response.setWeekTranslations(translationPort.countHistoryByUserIdAfter(userId, weekAgo));
        response.setMonthTranslations(translationPort.countHistoryByUserIdAfter(userId, monthAgo));

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
        var glossaryPage = glossaryPort.findGlossaryPaged(userId, search, page, pageSize);

        List<GlossaryResponse> list = glossaryPage.getRecords().stream()
                .map(this::toGlossaryResponse)
                .collect(Collectors.toList());

        return PageResponse.of(page, pageSize, glossaryPage.getTotal(), list);
    }

    /**
     * 获取术语库详情
     */
    public GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId) {
        return glossaryPort.findGlossaryById(glossaryId)
                .filter(g -> g.getDeleted() == null || g.getDeleted() != 1)
                .filter(g -> g.getUserId().equals(userId))
                .map(this::toGlossaryResponse)
                .orElse(null);
    }

    /**
     * 获取术语列表
     */
    public List<GlossaryResponse> getGlossaryTerms(Long userId) {
        return fetchAllGlossaries(userId);
    }

    private List<GlossaryResponse> fetchAllGlossaries(Long userId) {
        return glossaryPort.findGlossaryByUserId(userId).stream()
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

        glossaryPort.saveGlossary(glossary);
        return toGlossaryResponse(glossary);
    }

    /**
     * 更新术语项
     */
    public GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request) {
        return glossaryPort.findGlossaryById(glossaryId)
                .filter(g -> g.getDeleted() == null || g.getDeleted() != 1)
                .filter(g -> g.getUserId().equals(userId))
                .map(glossary -> {
                    if (request.getSourceWord() != null) {
                        glossary.setSourceWord(request.getSourceWord());
                    }
                    if (request.getTargetWord() != null) {
                        glossary.setTargetWord(request.getTargetWord());
                    }
                    if (request.getRemark() != null) {
                        glossary.setRemark(request.getRemark());
                    }
                    glossaryPort.updateGlossary(glossary);
                    return toGlossaryResponse(glossary);
                }).orElse(null);
    }

    /**
     * 删除术语项
     */
    public boolean deleteGlossaryItem(Long userId, Long glossaryId) {
        return glossaryPort.findGlossaryById(glossaryId)
                .filter(g -> g.getDeleted() == null || g.getDeleted() != 1)
                .filter(g -> g.getUserId().equals(userId))
                .map(g -> {
                    glossaryPort.updateGlossary(g);
                    return true;
                }).orElse(false);
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
        return userPort.findPreferenceByUserId(userId)
                .map(this::toPreferencesResponse)
                .orElseGet(this::buildDefaultPreferences);
    }

    /**
     * 更新用户偏好设置
     */
    public UserPreferencesResponse updateUserPreferences(Long userId, UserPreferencesRequest request) {
        return userPort.findPreferenceByUserId(userId).map(pref -> {
            if (request.getDefaultEngine() != null) pref.setDefaultEngine(request.getDefaultEngine());
            if (request.getDefaultTargetLang() != null) pref.setDefaultTargetLang(request.getDefaultTargetLang());
            if (request.getEnableGlossary() != null) pref.setEnableGlossary(request.getEnableGlossary());
            if (request.getDefaultGlossaryId() != null) pref.setDefaultGlossaryId(request.getDefaultGlossaryId());
            if (request.getEnableCache() != null) pref.setEnableCache(request.getEnableCache());
            if (request.getAutoTranslateSelection() != null) pref.setAutoTranslateSelection(request.getAutoTranslateSelection());
            if (request.getFontSize() != null) pref.setFontSize(request.getFontSize());
            if (request.getThemeMode() != null) pref.setThemeMode(request.getThemeMode());
            userPort.updatePreference(pref);
            return toPreferencesResponse(pref);
        }).orElseGet(() -> {
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setDefaultEngine(request.getDefaultEngine() != null ? request.getDefaultEngine() : "google");
            pref.setDefaultTargetLang(request.getDefaultTargetLang() != null ? request.getDefaultTargetLang() : "zh");
            pref.setEnableGlossary(request.getEnableGlossary() != null ? request.getEnableGlossary() : true);
            pref.setDefaultGlossaryId(request.getDefaultGlossaryId());
            pref.setEnableCache(request.getEnableCache() != null ? request.getEnableCache() : true);
            pref.setAutoTranslateSelection(request.getAutoTranslateSelection() != null ? request.getAutoTranslateSelection() : true);
            pref.setFontSize(request.getFontSize() != null ? request.getFontSize() : 14);
            pref.setThemeMode(request.getThemeMode() != null ? request.getThemeMode() : "light");
            userPort.savePreference(pref);
            return toPreferencesResponse(pref);
        });
    }

    /**
     * 获取平台统计信息（跨租户全局统计）
     */
    public PlatformStatsResponse getPlatformStats() {
        PlatformStatsResponse response = new PlatformStatsResponse();

        try {
            TenantContext.setBypassTenant(true);

            response.setTotalUsers(userPort.countActiveUsers());

            LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
            LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            response.setActiveUsersWeek(translationPort.countActiveUsersAfter(weekAgo));
            response.setActiveUsersMonth(translationPort.countActiveUsersAfter(monthAgo));
            response.setActiveUsersToday(translationPort.countActiveUsersAfter(todayStart));

            response.setTotalTranslations(translationPort.countAllHistory());
            response.setTranslationsToday(translationPort.countHistoryAfter(todayStart));
            response.setTotalCharacters(translationPort.sumAllHistorySourceTextLength());
            response.setTotalDocumentTranslations(translationPort.countDocumentTranslations());
            response.setTotalGlossaries(glossaryPort.countAllGlossaries());

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
