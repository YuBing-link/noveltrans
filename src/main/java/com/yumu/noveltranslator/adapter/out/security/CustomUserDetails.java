package com.yumu.noveltranslator.adapter.out.security;

import com.yumu.noveltranslator.domain.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 实现Spring Security的UserDetails接口
 */
public class CustomUserDetails implements UserDetails {

    private User user;
    private final Long userId;
    private final String email;
    private final String userLevel;
    private final Long tenantId;

    public CustomUserDetails(User user) {
        this.user = user;
        this.userId = user.getId();
        this.email = user.getEmail();
        this.userLevel = user.getUserLevel();
        this.tenantId = user.getTenantId();
    }

    /**
     * 轻量构造器（JWT 缓存命中时使用，无需加载完整 User 实体）
     */
    public CustomUserDetails(Long userId, String email, String userLevel, Long tenantId) {
        this.userId = userId;
        this.email = email;
        this.userLevel = userLevel;
        this.tenantId = tenantId;
    }

    /**
     * 轻量构造器（无 email 场景，如 API Key 认证）
     */
    public CustomUserDetails(Long userId, String userLevel, Long tenantId) {
        this(userId, null, userLevel, tenantId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 优先使用直接字段（轻量构造器路径）
        String level = userLevel != null ? userLevel : (user != null ? user.getUserLevel() : null);
        String role = "ROLE_" + (level != null ? level.toUpperCase() : "FREE");
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return user != null ? user.getPassword() : null; // 轻量路径下为 null
    }

    @Override
    public String getUsername() {
        return email != null ? email : (user != null ? user.getEmail() : "user-" + userId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // 账户未过期
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // 账户未锁定
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 凭据未过期
    }

    @Override
    public boolean isEnabled() {
        return true; // 账户已启用
    }

    // 提供对领域模型用户的访问
    public User getUser() {
        return user;
    }

    public Long getId() {
        return userId;
    }

    public String getEmail() {
        return email != null ? email : (user != null ? user.getEmail() : null);
    }

    public String getUserLevel() {
        return userLevel;
    }

    public Long getTenantId() {
        return tenantId;
    }
}