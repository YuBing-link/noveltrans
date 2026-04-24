package com.yumu.noveltranslator.config.tenant;

public final class TenantContext {

    // 使用 InheritableThreadLocal 以便虚拟线程继承父线程的租户上下文
    private static final InheritableThreadLocal<Long> CURRENT_TENANT = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<Boolean> BYPASS_TENANT = new InheritableThreadLocal<>();

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
