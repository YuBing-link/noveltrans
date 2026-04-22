package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.PlatformStatsResponse;
import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebPlatformControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private UserService userService;

    private WebPlatformController controller;

    @BeforeEach
    void setUp() {
        controller = new WebPlatformController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("平台统计信息")
    class PlatformStatsTests {

        @Test
        void 获取平台统计信息成功() throws Exception {
            PlatformStatsResponse stats = new PlatformStatsResponse();
            when(userService.getPlatformStats()).thenReturn(stats);

            mockMvc.perform(get("/platform/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 返回数据结构正确() throws Exception {
            PlatformStatsResponse stats = new PlatformStatsResponse();
            when(userService.getPlatformStats()).thenReturn(stats);

            mockMvc.perform(get("/platform/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        }
    }
}
