package com.yumu.noveltranslator.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void userGettersAndSetters() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setAvatar("https://example.com/avatar.png");
        user.setPassword("hashed");

        Map<String, String> keys = new HashMap<>();
        keys.put("google", "key123");
        user.setApiKey(keys);

        user.setRefreshToken("token");
        user.setUserLevel("free");
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setLastLoginTime(now);
        user.setDeleted(0);

        assertEquals(1L, user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("testuser", user.getUsername());
        assertEquals("https://example.com/avatar.png", user.getAvatar());
        assertEquals("hashed", user.getPassword());
        assertEquals("key123", user.getApiKey().get("google"));
        assertEquals("token", user.getRefreshToken());
        assertEquals("free", user.getUserLevel());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals(now, user.getCreateTime());
        assertEquals(0, user.getDeleted());
    }
}
