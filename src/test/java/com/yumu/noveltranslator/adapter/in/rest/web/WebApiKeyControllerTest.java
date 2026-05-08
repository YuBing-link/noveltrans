package com.yumu.noveltranslator.adapter.in.rest.web;
import com.yumu.noveltranslator.domain.model.User;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.adapter.out.security.CustomUserDetails;
import com.yumu.noveltranslator.port.in.ApiKeyPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebApiKeyControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private ApiKeyPort apiKeyPort;

    private WebApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new WebApiKeyController(apiKeyPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void setupSecurityContext() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ApiKey createTestApiKey() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setUserId(1L);
        key.setApiKey("nt_sk_abc123def456ghi789jkl012mno345pq");
        key.setName("Test Key");
        key.setActive(true);
        key.setTotalUsage(0L);
        key.setCreatedAt(LocalDateTime.now());
        return key;
    }

    @Nested
    @DisplayName("创建 API Key")
    class CreateApiKeyTests {

        @Test
        void 创建APIKey成功() throws Exception {
            setupSecurityContext();
            ApiKey key = createTestApiKey();
            when(apiKeyPort.createApiKey(eq(1L), any())).thenReturn(key);

            mockMvc.perform(post("/user/api-keys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"My Key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.apiKey").exists());
        }

        @Test
        void 创建APIKey无名称使用默认值() throws Exception {
            setupSecurityContext();
            ApiKey key = createTestApiKey();
            key.setName("Default");
            when(apiKeyPort.createApiKey(eq(1L), any())).thenReturn(key);

            mockMvc.perform(post("/user/api-keys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Default"));
        }
    }

    @Nested
    @DisplayName("获取 API Key 列表")
    class GetApiKeysTests {

        @Test
        void 获取APIKey列表成功() throws Exception {
            setupSecurityContext();
            ApiKey key = createTestApiKey();
            PageResponse<ApiKey> page = PageResponse.of(1, 20, 1L, List.of(key));
            when(apiKeyPort.listApiKeys(eq(1L), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/user/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list.length()").value(1));
        }

        @Test
        void 空列表返回成功() throws Exception {
            setupSecurityContext();
            PageResponse<ApiKey> page = PageResponse.of(1, 20, 0L, List.of());
            when(apiKeyPort.listApiKeys(eq(1L), eq(1), eq(20))).thenReturn(page);

            mockMvc.perform(get("/user/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list.length()").value(0));
        }
    }

    @Nested
    @DisplayName("删除 API Key")
    class DeleteApiKeyTests {

        @Test
        void 删除APIKey成功() throws Exception {
            setupSecurityContext();
            when(apiKeyPort.deleteApiKey(eq(1L), eq(1L))).thenReturn(true);

            mockMvc.perform(delete("/user/api-keys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void APIKey不存在返回错误() throws Exception {
            setupSecurityContext();
            when(apiKeyPort.deleteApiKey(eq(999L), eq(1L))).thenReturn(false);

            mockMvc.perform(delete("/user/api-keys/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("API Key 不存在"));
        }
    }

    @Nested
    @DisplayName("重置 API Key")
    class ResetApiKeyTests {

        @Test
        void 重置APIKey成功() throws Exception {
            setupSecurityContext();
            ApiKey key = createTestApiKey();
            key.setApiKey("nt_sk_new_reset_key_abc123def456ghi789jkl012m");
            when(apiKeyPort.resetApiKey(eq(1L), eq(1L))).thenReturn(key);

            mockMvc.perform(post("/user/api-keys/1/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.apiKey").exists());
        }

        @Test
        void APIKey不存在返回错误() throws Exception {
            setupSecurityContext();
            when(apiKeyPort.resetApiKey(eq(999L), eq(1L))).thenReturn(null);

            mockMvc.perform(post("/user/api-keys/999/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }
    }
}
