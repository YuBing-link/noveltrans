package com.yumu.noveltranslator.util;

import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityUtil 单元测试")
class SecurityUtilTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CustomUserDetails createTestUserDetails() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("hashedPassword");
        user.setUserLevel("FREE");
        return new CustomUserDetails(user);
    }

    @Nested
    @DisplayName("getCurrentUserId 测试")
    class GetCurrentUserIdTests {

        @Test
        @DisplayName("已认证用户返回用户ID")
        void 已认证用户返回用户ID() {
            CustomUserDetails userDetails = createTestUserDetails();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);

            var result = SecurityUtil.getCurrentUserId();

            assertTrue(result.isPresent());
            assertEquals(1L, result.get());
        }

        @Test
        @DisplayName("未认证用户返回Optional.empty()")
        void 未认证用户返回空() {
            SecurityContextHolder.clearContext();

            var result = SecurityUtil.getCurrentUserId();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("principal类型不是CustomUserDetails返回Optional.empty()")
        void 错误的principal类型返回空() {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken("string-principal", null));
            SecurityContextHolder.setContext(context);

            var result = SecurityUtil.getCurrentUserId();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getRequiredUserId 测试")
    class GetRequiredUserIdTests {

        @Test
        @DisplayName("已认证用户返回用户ID")
        void 已认证用户返回用户ID() {
            CustomUserDetails userDetails = createTestUserDetails();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);

            Long result = SecurityUtil.getRequiredUserId();

            assertEquals(1L, result);
        }

        @Test
        @DisplayName("未认证用户抛出IllegalStateException")
        void 未认证用户抛出异常() {
            SecurityContextHolder.clearContext();

            assertThrows(IllegalStateException.class, SecurityUtil::getRequiredUserId);
        }
    }

    @Nested
    @DisplayName("getCurrentUserDetails 测试")
    class GetCurrentUserDetailsTests {

        @Test
        @DisplayName("已认证用户返回CustomUserDetails")
        void 已认证用户返回CustomUserDetails() {
            CustomUserDetails userDetails = createTestUserDetails();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);

            var result = SecurityUtil.getCurrentUserDetails();

            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail());
        }

        @Test
        @DisplayName("getRequiredUserDetails已认证返回CustomUserDetails")
        void getRequiredUserDetails已认证返回CustomUserDetails() {
            CustomUserDetails userDetails = createTestUserDetails();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);

            CustomUserDetails result = SecurityUtil.getRequiredUserDetails();

            assertNotNull(result);
            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("getRequiredUserDetails未认证抛出IllegalStateException")
        void getRequiredUserDetails未认证抛出异常() {
            SecurityContextHolder.clearContext();

            assertThrows(IllegalStateException.class, SecurityUtil::getRequiredUserDetails);
        }
    }
}
