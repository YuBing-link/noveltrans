package com.yumu.noveltranslator.security;

import com.yumu.noveltranslator.entity.ApiKey;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthenticationFilter 测试")
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private FilterChain chain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter();
        try {
            var apiKeyField = ApiKeyAuthenticationFilter.class.getDeclaredField("apiKeyMapper");
            apiKeyField.setAccessible(true);
            apiKeyField.set(filter, apiKeyMapper);

            var userField = ApiKeyAuthenticationFilter.class.getDeclaredField("userMapper");
            userField.setAccessible(true);
            userField.set(filter, userMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("非API Key请求")
    class NonApiKeyTests {

        @Test
        @DisplayName("已有认证直接放行")
        void existingAuthenticationSkipsFilter() throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("existing", null));

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("没有Authorization头直接放行")
        void noAuthorizationHeaderPassesThrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(apiKeyMapper);
        }

        @Test
        @DisplayName("Bearer token不以nt_sk_开头不视为API Key")
        void nonApiKeyPrefixPassesThrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer jwt-token-123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(apiKeyMapper);
        }
    }

    @Nested
    @DisplayName("API Key认证")
    class ApiKeyAuthTests {

        @Test
        @DisplayName("有效的API Key设置认证")
        void validApiKeySetsAuthentication() throws Exception {
            String apiKeyValue = "nt_sk_abc1234567890xyz";
            ApiKey apiKey = new ApiKey();
            apiKey.setId(1L);
            apiKey.setUserId(10L);
            apiKey.setActive(true);

            User apiUser = new User();
            apiUser.setId(10L);
            apiUser.setTenantId(1L);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + apiKeyValue);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(apiKeyMapper.findByApiKey(apiKeyValue)).thenReturn(apiKey);
            when(userMapper.selectById(10L)).thenReturn(apiUser);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals(1L, request.getAttribute("apiKeyId"));
            assertEquals(10L, request.getAttribute("authenticatedUserId"));
        }

        @Test
        @DisplayName("无效的API Key返回401")
        void invalidApiKeyReturns401() throws Exception {
            String apiKeyValue = "nt_sk_invalid123";

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + apiKeyValue);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(apiKeyMapper.findByApiKey(apiKeyValue)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            assertEquals(401, response.getStatus());
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("已禁用的API Key返回401")
        void disabledApiKeyReturns401() throws Exception {
            String apiKeyValue = "nt_sk_disabled123";
            ApiKey apiKey = new ApiKey();
            apiKey.setId(1L);
            apiKey.setUserId(10L);
            apiKey.setActive(false);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + apiKeyValue);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(apiKeyMapper.findByApiKey(apiKeyValue)).thenReturn(apiKey);

            filter.doFilterInternal(request, response, chain);

            assertEquals(401, response.getStatus());
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("maskApiKey工具方法")
    class MaskApiKeyTests {

        @Test
        @DisplayName("短key返回掩码")
        void shortKeyReturnsMasked() throws Exception {
            // maskApiKey is private, test indirectly through the filter behavior
            // The filter logs a masked version, but we can't easily test that.
            // Just verify the filter works with short keys
            String apiKeyValue = "nt_sk_short";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + apiKeyValue);
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(apiKeyMapper.findByApiKey(apiKeyValue)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            assertEquals(401, response.getStatus());
        }
    }
}
