package com.yumu.noveltranslator.util;

import com.yumu.noveltranslator.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        return getCurrentUserDetails().map(userDetails -> userDetails.getUser().getId());
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
        return getRequiredUserDetails().getUser().getId();
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
}
