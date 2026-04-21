package com.yumu.noveltranslator.security;

import java.util.List;

/**
 * 安全白名单路径配置
 * 集中管理所有不需要认证的路径，供 SecurityConfig 和 JwtAuthenticationFilter 共享使用。
 */
public final class SecurityPermitAllPaths {

    /** 不需要认证的路径列表 */
    public static final List<String> PERMIT_ALL_PATHS = List.of(
        "/user/login",
        "/user/register",
        "/user/send-code",
        "/user/send-reset-code",
        "/user/reset-password",
        "/user/get-token",
        "/health",
        "/actuator",
        "/v1/translate/selection",
        "/v1/translate/reader",
        "/v1/translate/webpage",
        "/v1/translate/text",
        "/v1/translate/text/stream",
        "/v1/translate/document",
        "/v1/translate/document/stream",
        "/v1/translate/task",
        "/v1/translate/rag",
        "/platform",
        "/platform/stats",
        "/static",
        "/css",
        "/js",
        "/images",
        "/swagger-ui",
        "/v3/api-docs"
    );

    private SecurityPermitAllPaths() {
        // 工具类，禁止实例化
    }

    /** 判断给定请求 URI 是否在白名单中 */
    public static boolean isPermitted(String requestURI) {
        if (requestURI == null) {
            return false;
        }
        return PERMIT_ALL_PATHS.stream().anyMatch(
            path -> requestURI.equals(path) || requestURI.startsWith(path + "/")
        );
    }
}
