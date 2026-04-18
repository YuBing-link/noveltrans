package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void hashPasswordReturnsNonEmptyString() {
        String hash = PasswordUtil.hashPassword("myPassword123");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    void hashPasswordProducesDifferentHashesForSamePassword() {
        String hash1 = PasswordUtil.hashPassword("myPassword123");
        String hash2 = PasswordUtil.hashPassword("myPassword123");
        // BCrypt 每次生成不同 hash
        assertNotEquals(hash1, hash2);
    }

    @Test
    void verifyPasswordWithCorrectPassword() {
        String password = "myPassword123";
        String hash = PasswordUtil.hashPassword(password);
        assertTrue(PasswordUtil.verifyPassword(password, hash));
    }

    @Test
    void verifyPasswordWithWrongPassword() {
        String hash = PasswordUtil.hashPassword("correctPassword");
        assertFalse(PasswordUtil.verifyPassword("wrongPassword", hash));
    }

    @Test
    void hashPasswordThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));
    }
}
