package com.yumu.noveltranslator.util;

import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 安全认证工具类
 * 统一处理 Controller 中的认证代码提取
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前认证用户ID，如果未认证则返回空
     */
    public static Optional<Long> getCurrentUserId() {
        return getCurrentUserDetails().map(CustomUserDetails::getId);
    }

    /**
     * 获取当前认证用户的 userLevel，如果未认证则返回空
     */
    public static Optional<String> getCurrentUserLevel() {
        return getCurrentUserDetails().map(CustomUserDetails::getUserLevel);
    }

    /**
     * 获取当前认证用户详情，如果未认证则返回空
     */
    public static Optional<CustomUserDetails> getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return Optional.of((CustomUserDetails) authentication.getPrincipal());
        }
        return Optional.empty();
    }

    /**
     * 获取当前认证用户ID，如果未认证则抛出异常
     */
    public static Long getRequiredUserId() {
        return getRequiredUserDetails().getId();
    }

    /**
     * 获取当前认证用户详情，如果未认证则抛出异常
     */
    public static CustomUserDetails getRequiredUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new IllegalStateException("未认证的用户");
        }
        return (CustomUserDetails) authentication.getPrincipal();
    }

    /**
     * 从 Authorization header 中解析 Bearer token
     * @return token 字符串，如果没有或格式不对则返回 null
     */
    public static String parseBearerToken(HttpServletRequest request) {
        return parseBearerToken(request.getHeader("Authorization"));
    }

    /**
     * 从 Authorization header 字符串中解析 Bearer token
     */
    public static String parseBearerToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 提取真实客户端 IP，检查代理头
     */
    public static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xri)) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 掩码 API Key（显示前6位 + "****" + 后4位）
     */
    public static String maskApiKey(String key) {
        if (key == null || key.length() < 16) return "***";
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }

    /**
     * 获取当前用户 ID 字符串，未认证时返回 "anonymous"
     */
    public static String getCurrentUserIdOrAnonymous() {
        return getCurrentUserDetails()
                .map(userDetails -> "user_" + userDetails.getId())
                .orElse("anonymous");
    }

    /**
     * 获取当前用户等级，未认证时返回 "anonymous"
     */
    public static String getCurrentUserLevelOrDefault() {
        return getCurrentUserDetails()
                .map(CustomUserDetails::getUserLevel)
                .orElse("anonymous");
    }
}
