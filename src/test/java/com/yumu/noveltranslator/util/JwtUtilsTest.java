package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        jwtUtils.setSecret("test-secret-key-for-jwt-testing");
        jwtUtils.setExpireTime(3600000L); // 1 hour
    }

    @Test
    void createTokenReturnsNonEmptyString() {
        String token = jwtUtils.createToken(1L, "test@example.com");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void verifyTokenSucceedsWithValidToken() {
        String token = jwtUtils.createToken(1L, "test@example.com");
        var decoded = jwtUtils.verifyToken(token);
        assertNotNull(decoded);
    }

    @Test
    void getUserInfoFromTokenReturnsCorrectData() {
        String token = jwtUtils.createToken(42L, "user@example.com");
        var info = jwtUtils.getUserInfoFromToken(token);

        assertEquals("42", info.get("userId"));
        assertEquals("user@example.com", info.get("email"));
    }

    @Test
    void getUserIdFromTokenReturnsCorrectId() {
        String token = jwtUtils.createToken(123L, "test@example.com");
        assertEquals(123L, jwtUtils.getUserIdFromToken(token));
    }

    @Test
    void getEmailFromTokenReturnsCorrectEmail() {
        String token = jwtUtils.createToken(1L, "specific@example.com");
        assertEquals("specific@example.com", jwtUtils.getEmailFromToken(token));
    }

    @Test
    void verifyTokenFailsWithInvalidToken() {
        assertThrows(Exception.class, () -> jwtUtils.verifyToken("invalid.token.here"));
    }

    @Test
    void verifyTokenFailsWithTamperedToken() {
        String token = jwtUtils.createToken(1L, "test@example.com");
        String tampered = token + "tampered";
        assertThrows(Exception.class, () -> jwtUtils.verifyToken(tampered));
    }

    @Test
    void differentUsersProduceDifferentTokens() {
        String token1 = jwtUtils.createToken(1L, "user1@example.com");
        String token2 = jwtUtils.createToken(2L, "user2@example.com");
        assertNotEquals(token1, token2);
    }
}
