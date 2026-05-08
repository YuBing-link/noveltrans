package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPreference;

public final class UserPreferenceConverter {
    private UserPreferenceConverter() {}
    public static com.yumu.noveltranslator.domain.model.UserPreference toModel(UserPreference e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.UserPreference();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setDefaultEngine(e.getDefaultEngine());
        m.setDefaultTargetLang(e.getDefaultTargetLang()); m.setEnableGlossary(e.getEnableGlossary());
        m.setDefaultGlossaryId(e.getDefaultGlossaryId()); m.setEnableCache(e.getEnableCache());
        m.setAutoTranslateSelection(e.getAutoTranslateSelection()); m.setFontSize(e.getFontSize());
        m.setThemeMode(e.getThemeMode()); m.setTenantId(e.getTenantId());
        m.setCreateTime(e.getCreateTime()); m.setUpdateTime(e.getUpdateTime()); return m;
    }
    public static UserPreference toEntity(com.yumu.noveltranslator.domain.model.UserPreference m) {
        if (m == null) return null;
        var e = new UserPreference();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setDefaultEngine(m.getDefaultEngine());
        e.setDefaultTargetLang(m.getDefaultTargetLang()); e.setEnableGlossary(m.getEnableGlossary());
        e.setDefaultGlossaryId(m.getDefaultGlossaryId()); e.setEnableCache(m.getEnableCache());
        e.setAutoTranslateSelection(m.getAutoTranslateSelection()); e.setFontSize(m.getFontSize());
        e.setThemeMode(m.getThemeMode()); e.setTenantId(m.getTenantId());
        e.setCreateTime(m.getCreateTime()); e.setUpdateTime(m.getUpdateTime()); return e;
    }
}
