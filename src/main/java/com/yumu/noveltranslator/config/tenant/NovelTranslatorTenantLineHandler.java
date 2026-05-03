package com.yumu.noveltranslator.config.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

public class NovelTranslatorTenantLineHandler implements TenantLineHandler {

    private static final Set<String> IGNORE_TABLES = Set.of(
        "translation_cache",
        "email_verification_code",
        "user",
        "collab_invite_code",
        "api_keys",
        "stripe_customer",
        "stripe_subscription",
        "user_plan_history"
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return new LongValue(0L);
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.isBypassTenant()) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName);
    }
}
