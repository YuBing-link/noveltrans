package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    @Test
    void getUsernameReturnsEmail() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("hashed");
        user.setUserLevel("free");

        CustomUserDetails details = new CustomUserDetails(user);
        assertEquals("test@example.com", details.getUsername());
        assertEquals("hashed", details.getPassword());
    }

    @Test
    void getAuthoritiesReturnsRoleBasedOnUserLevel() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setUserLevel("pro");

        CustomUserDetails details = new CustomUserDetails(user);
        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("ROLE_PRO", authorities.iterator().next().getAuthority());
    }

    @Test
    void getAuthoritiesDefaultRoleForFree() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setUserLevel("free");

        CustomUserDetails details = new CustomUserDetails(user);
        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertEquals("ROLE_FREE", authorities.iterator().next().getAuthority());
    }

    @Test
    void getUserReturnsOriginalUser() {
        User user = new User();
        user.setId(42L);
        user.setEmail("test@example.com");

        CustomUserDetails details = new CustomUserDetails(user);
        assertSame(user, details.getUser());
        assertEquals(42L, details.getId());
        assertEquals("test@example.com", details.getEmail());
    }

    @Test
    void getUserLevel() {
        User user = new User();
        user.setUserLevel("premium");
        CustomUserDetails details = new CustomUserDetails(user);
        assertEquals("premium", details.getUserLevel());
    }

    @Test
    void isAccountNonExpired() {
        User user = new User();
        CustomUserDetails details = new CustomUserDetails(user);
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
        assertTrue(details.isEnabled());
    }

    @Test
    void userWithNullLevelReturnsRoleFree() {
        User user = new User();
        user.setUserLevel(null);
        CustomUserDetails details = new CustomUserDetails(user);
        Collection<? extends GrantedAuthority> authorities = details.getAuthorities();
        assertEquals("ROLE_FREE", authorities.iterator().next().getAuthority());
    }
}
