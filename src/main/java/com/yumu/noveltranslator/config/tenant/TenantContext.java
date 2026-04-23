package com.yumu.noveltranslator.config.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setBypassTenant(boolean bypass) {
        BYPASS_TENANT.set(bypass);
    }

    public static boolean isBypassTenant() {
        return Boolean.TRUE.equals(BYPASS_TENANT.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        BYPASS_TENANT.remove();
    }
}
