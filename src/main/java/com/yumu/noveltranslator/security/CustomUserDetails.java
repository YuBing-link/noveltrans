package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 实现Spring Security的UserDetails接口
 */
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 为用户分配基于其级别（free/pro）的权限
        String role = "ROLE_" + (user.getUserLevel() != null ? user.getUserLevel().toUpperCase() : "FREE");
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // 这是加密后的密码
    }

    @Override
    public String getUsername() {
        return user.getEmail();
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

    // 提供对原始用户对象的访问
    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getUserLevel() {
        return user.getUserLevel();
    }
}