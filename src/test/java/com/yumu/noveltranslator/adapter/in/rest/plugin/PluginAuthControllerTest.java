package com.yumu.noveltranslator.adapter.in.rest.plugin;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.adapter.out.security.CustomUserDetails;
import com.yumu.noveltranslator.port.in.AuthPort;
import com.yumu.noveltranslator.port.in.DeviceTokenPort;
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

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PluginAuthControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private AuthPort authPort;

    @org.mockito.Mock
    private DeviceTokenPort deviceTokenPort;

    private PluginAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new PluginAuthController(authPort, deviceTokenPort);
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

    @Nested
    @DisplayName("注册设备 Token")
    class RegisterDeviceTests {

        @Test
        void 注册设备成功() throws Exception {
            setupSecurityContext();
            User user = new User();
            user.setId(1L);
            user.setEmail("test@test.com");
            when(authPort.getUserById(1L)).thenReturn(Optional.of(user));
            when(deviceTokenPort.generateAndRegisterToken(eq("device-001"), eq(1L), eq("test@test.com"), any())).thenReturn("new-token");

            mockMvc.perform(post("/user/register-device")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deviceId\":\"device-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("设备注册成功"));
        }

        @Test
        void 设备ID为空返回错误() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/user/register-device")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("设备 ID 不能为空"));
        }
    }

    @Nested
    @DisplayName("获取 Token")
    class GetTokenTests {

        @Test
        void 获取Token成功() throws Exception {
            when(deviceTokenPort.verifyAndGetUserInfo("device-001")).thenReturn(Map.of("userId", "1", "email", "test@test.com"));

            mockMvc.perform(get("/user/get-token/device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
        }

        @Test
        void 设备未登录返回404() throws Exception {
            when(deviceTokenPort.verifyAndGetUserInfo("unknown-device")).thenReturn(null);

            mockMvc.perform(get("/user/get-token/unknown-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未找到登录信息，请先在网站登录"));
        }

        @Test
        void Token过期返回设备未登录() throws Exception {
            when(deviceTokenPort.verifyAndGetUserInfo("device-001")).thenReturn(null);

            mockMvc.perform(get("/user/get-token/device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未找到登录信息，请先在网站登录"));
        }
    }
}
