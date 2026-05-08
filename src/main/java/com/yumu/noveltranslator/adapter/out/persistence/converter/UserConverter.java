package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.*;

import java.util.List;
import java.util.stream.Collectors;

public final class UserConverter {

    private UserConverter() {}

    // === User ===

    public static com.yumu.noveltranslator.domain.model.User toUserModel(User entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.User();
        model.setId(entity.getId());
        model.setEmail(entity.getEmail());
        model.setUsername(entity.getUsername());
        model.setAvatar(entity.getAvatar());
        model.setPassword(entity.getPassword());
        model.setApiKey(entity.getApiKey());
        model.setRefreshToken(entity.getRefreshToken());
        model.setUserLevel(entity.getUserLevel());
        model.setStatus(entity.getStatus());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        model.setLastLoginTime(entity.getLastLoginTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static User toUserEntity(com.yumu.noveltranslator.domain.model.User model) {
        if (model == null) return null;
        var entity = new User();
        entity.setId(model.getId());
        entity.setEmail(model.getEmail());
        entity.setUsername(model.getUsername());
        entity.setAvatar(model.getAvatar());
        entity.setPassword(model.getPassword());
        entity.setApiKey(model.getApiKey());
        entity.setRefreshToken(model.getRefreshToken());
        entity.setUserLevel(model.getUserLevel());
        entity.setStatus(model.getStatus());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        entity.setLastLoginTime(model.getLastLoginTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    // === UserPreference ===

    public static com.yumu.noveltranslator.domain.model.UserPreference toPreferenceModel(UserPreference entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.UserPreference();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setDefaultEngine(entity.getDefaultEngine());
        model.setDefaultTargetLang(entity.getDefaultTargetLang());
        model.setEnableGlossary(entity.getEnableGlossary());
        model.setDefaultGlossaryId(entity.getDefaultGlossaryId());
        model.setEnableCache(entity.getEnableCache());
        model.setAutoTranslateSelection(entity.getAutoTranslateSelection());
        model.setFontSize(entity.getFontSize());
        model.setThemeMode(entity.getThemeMode());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    public static UserPreference toPreferenceEntity(com.yumu.noveltranslator.domain.model.UserPreference model) {
        if (model == null) return null;
        var entity = new UserPreference();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setDefaultEngine(model.getDefaultEngine());
        entity.setDefaultTargetLang(model.getDefaultTargetLang());
        entity.setEnableGlossary(model.getEnableGlossary());
        entity.setDefaultGlossaryId(model.getDefaultGlossaryId());
        entity.setEnableCache(model.getEnableCache());
        entity.setAutoTranslateSelection(model.getAutoTranslateSelection());
        entity.setFontSize(model.getFontSize());
        entity.setThemeMode(model.getThemeMode());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        return entity;
    }

    // === UserPlanHistory ===

    public static com.yumu.noveltranslator.domain.model.UserPlanHistory toPlanHistoryModel(UserPlanHistory entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.UserPlanHistory();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setOldPlan(entity.getOldPlan());
        model.setNewPlan(entity.getNewPlan());
        model.setTenantId(entity.getTenantId());
        model.setChangedAt(entity.getChangedAt());
        model.setNote(entity.getNote());
        return model;
    }

    public static UserPlanHistory toPlanHistoryEntity(com.yumu.noveltranslator.domain.model.UserPlanHistory model) {
        if (model == null) return null;
        var entity = new UserPlanHistory();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setOldPlan(model.getOldPlan());
        entity.setNewPlan(model.getNewPlan());
        entity.setTenantId(model.getTenantId());
        entity.setChangedAt(model.getChangedAt());
        entity.setNote(model.getNote());
        return entity;
    }

    // === Tenant ===

    public static com.yumu.noveltranslator.domain.model.Tenant toTenantModel(Tenant entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.Tenant();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setStatus(entity.getStatus());
        model.setMaxUsers(entity.getMaxUsers());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    public static Tenant toTenantEntity(com.yumu.noveltranslator.domain.model.Tenant model) {
        if (model == null) return null;
        var entity = new Tenant();
        entity.setId(model.getId());
        entity.setName(model.getName());
        entity.setStatus(model.getStatus());
        entity.setMaxUsers(model.getMaxUsers());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        return entity;
    }

    // === ApiKey ===

    public static com.yumu.noveltranslator.domain.model.ApiKey toApiKeyModel(ApiKey entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.ApiKey();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setApiKey(entity.getApiKey());
        model.setName(entity.getName());
        model.setActive(entity.getActive());
        model.setLastUsedAt(entity.getLastUsedAt());
        model.setTotalUsage(entity.getTotalUsage());
        model.setTenantId(entity.getTenantId());
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }

    public static ApiKey toApiKeyEntity(com.yumu.noveltranslator.domain.model.ApiKey model) {
        if (model == null) return null;
        var entity = new ApiKey();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setApiKey(model.getApiKey());
        entity.setName(model.getName());
        entity.setActive(model.getActive());
        entity.setLastUsedAt(model.getLastUsedAt());
        entity.setTotalUsage(model.getTotalUsage());
        entity.setTenantId(model.getTenantId());
        entity.setCreatedAt(model.getCreatedAt());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.ApiKey> toApiKeyModelList(List<ApiKey> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(UserConverter::toApiKeyModel).collect(Collectors.toList());
    }

    // === TokenBlacklist ===

    public static com.yumu.noveltranslator.domain.model.TokenBlacklist toBlacklistModel(TokenBlacklist entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.TokenBlacklist();
        model.setId(entity.getId());
        model.setToken(entity.getToken());
        model.setEmail(entity.getEmail());
        model.setReason(entity.getReason());
        model.setExpiresAt(entity.getExpiresAt());
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }

    public static TokenBlacklist toBlacklistEntity(com.yumu.noveltranslator.domain.model.TokenBlacklist model) {
        if (model == null) return null;
        var entity = new TokenBlacklist();
        entity.setId(model.getId());
        entity.setToken(model.getToken());
        entity.setEmail(model.getEmail());
        entity.setReason(model.getReason());
        entity.setExpiresAt(model.getExpiresAt());
        entity.setCreatedAt(model.getCreatedAt());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.TokenBlacklist> toBlacklistModelList(List<TokenBlacklist> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(UserConverter::toBlacklistModel).collect(Collectors.toList());
    }
}
