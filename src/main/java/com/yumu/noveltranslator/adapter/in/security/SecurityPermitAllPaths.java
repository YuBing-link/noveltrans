package com.yumu.noveltranslator.adapter.in.security;

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
        "/v1/webhook/stripe",
        // Swagger UI & OpenAPI
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/v3/api-docs"
    );

    private SecurityPermitAllPaths() {
        // 工具类，禁止实例化
    }

    /** 判断给定请求 URI 是否在白名单中，支持 /** 通配符 */
    public static boolean isPermitted(String requestURI) {
        if (requestURI == null) {
            return false;
        }
        for (String pattern : PERMIT_ALL_PATHS) {
            if (pattern.endsWith("/**")) {
                // 前缀匹配：/swagger-ui/** 匹配 /swagger-ui/ 及其所有子路径
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (requestURI.equals(prefix) || requestURI.startsWith(prefix + "/")) {
                    return true;
                }
            } else {
                if (requestURI.equals(pattern) || requestURI.startsWith(pattern + "/")) {
                    return true;
                }
            }
        }
        return false;
    }
}
