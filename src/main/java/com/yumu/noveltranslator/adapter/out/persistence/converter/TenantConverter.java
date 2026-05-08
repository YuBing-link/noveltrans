package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.Tenant;

public final class TenantConverter {
    private TenantConverter() {}
    public static com.yumu.noveltranslator.domain.model.Tenant toModel(Tenant e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.Tenant();
        m.setId(e.getId()); m.setName(e.getName()); m.setStatus(e.getStatus());
        m.setMaxUsers(e.getMaxUsers()); m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime()); return m;
    }
    public static Tenant toEntity(com.yumu.noveltranslator.domain.model.Tenant m) {
        if (m == null) return null;
        var e = new Tenant();
        e.setId(m.getId()); e.setName(m.getName()); e.setStatus(m.getStatus());
        e.setMaxUsers(m.getMaxUsers()); e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime()); return e;
    }
}
