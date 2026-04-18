package com.yumu.noveltranslator.config;

import java.util.List;

/**
 * 安全白名单路径配置
 * 集中管理所有不需要 JWT 认证的路径，供 SecurityConfig 和 JwtAuthenticationFilter 共享使用。
 */
public final class SecurityPermitAllPaths {

    /**
     * 不需要认证的路径列表（不含 /** 后缀）
     * JwtAuthenticationFilter 会将其作为前缀匹配（精确匹配或以该路径开头的子路径）。
     */
    public static final List<String> PERMIT_ALL_PATHS = List.of(
        "/user/login",
        "/user/register",
        "/user/send-code",
        "/user/reset-password",
        "/user/get-token",
        "/health",
        "/actuator",
        "/v1/translate",
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

    /**
     * 判断给定请求 URI 是否在白名单中
     * 匹配规则：精确匹配 或 以白名单路径为前缀（后接 / 或等于路径本身）
     */
    public static boolean isPermitted(String requestURI) {
        if (requestURI == null) {
            return false;
        }
        return PERMIT_ALL_PATHS.stream().anyMatch(
            path -> requestURI.equals(path) || requestURI.startsWith(path + "/")
        );
    }
}
