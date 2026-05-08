package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPlanHistory;

public final class UserPlanHistoryConverter {
    private UserPlanHistoryConverter() {}
    public static com.yumu.noveltranslator.domain.model.UserPlanHistory toModel(UserPlanHistory e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.UserPlanHistory();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setOldPlan(e.getOldPlan());
        m.setNewPlan(e.getNewPlan()); m.setTenantId(e.getTenantId());
        m.setChangedAt(e.getChangedAt()); m.setNote(e.getNote()); return m;
    }
    public static UserPlanHistory toEntity(com.yumu.noveltranslator.domain.model.UserPlanHistory m) {
        if (m == null) return null;
        var e = new UserPlanHistory();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setOldPlan(m.getOldPlan());
        e.setNewPlan(m.getNewPlan()); e.setTenantId(m.getTenantId());
        e.setChangedAt(m.getChangedAt()); e.setNote(m.getNote()); return e;
    }
}
