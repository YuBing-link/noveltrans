package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.QuotaUsage;
import java.util.List;
import java.util.stream.Collectors;

public final class QuotaConverter {
    private QuotaConverter() {}
    public static com.yumu.noveltranslator.domain.model.QuotaUsage toModel(QuotaUsage e) {
        if (e == null) return null;
        var m = new com.yumu.noveltranslator.domain.model.QuotaUsage();
        m.setId(e.getId()); m.setUserId(e.getUserId()); m.setUsageDate(e.getUsageDate());
        m.setCharactersUsed(e.getCharactersUsed()); m.setTenantId(e.getTenantId());
        m.setCreatedAt(e.getCreatedAt()); m.setUpdatedAt(e.getUpdatedAt()); return m;
    }
    public static QuotaUsage toEntity(com.yumu.noveltranslator.domain.model.QuotaUsage m) {
        if (m == null) return null;
        var e = new QuotaUsage();
        e.setId(m.getId()); e.setUserId(m.getUserId()); e.setUsageDate(m.getUsageDate());
        e.setCharactersUsed(m.getCharactersUsed()); e.setTenantId(m.getTenantId());
        e.setCreatedAt(m.getCreatedAt()); e.setUpdatedAt(m.getUpdatedAt()); return e;
    }
    public static List<com.yumu.noveltranslator.domain.model.QuotaUsage> toModelList(List<QuotaUsage> l) {
        if (l == null) return null; return l.stream().map(QuotaConverter::toModel).collect(Collectors.toList());
    }
}
