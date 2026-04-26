package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WebGlossaryControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private UserService userService;

    @org.mockito.Mock
    private GlossaryMapper glossaryMapper;

    private WebGlossaryController controller;

    @BeforeEach
    void setUp() {
        controller = new WebGlossaryController(userService, glossaryMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void setupSecurityContext() {
        com.yumu.noveltranslator.entity.User user = new com.yumu.noveltranslator.entity.User();
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

    private GlossaryResponse createTestGlossary() {
        GlossaryResponse glossary = new GlossaryResponse();
        glossary.setId(1L);
        glossary.setSourceWord("Hello");
        glossary.setTargetWord("你好");
        return glossary;
    }

    @Nested
    @DisplayName("获取术语库列表")
    class GetGlossaryListTests {

        @Test
        void 获取术语库列表成功() throws Exception {
            setupSecurityContext();
            PageResponse<GlossaryResponse> page = PageResponse.of(1, 20, 1L, List.of(createTestGlossary()));
            when(userService.getGlossaryList(eq(1L), eq(1), eq(20), eq(null))).thenReturn(page);

            mockMvc.perform(get("/user/glossaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list.length()").value(1));
        }
    }

    @Nested
    @DisplayName("获取术语库详情")
    class GetGlossaryDetailTests {

        @Test
        void 获取详情成功() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.getGlossaryDetail(1L, 1L)).thenReturn(glossary);

            mockMvc.perform(get("/user/glossaries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        void 术语库不存在返回错误() throws Exception {
            setupSecurityContext();
            when(userService.getGlossaryDetail(1L, 999L)).thenReturn(null);

            mockMvc.perform(get("/user/glossaries/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("术语库不存在"));
        }
    }

    @Nested
    @DisplayName("创建术语项")
    class CreateGlossaryItemTests {

        @Test
        void 创建术语项成功() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.createGlossaryItem(eq(1L), any(GlossaryItemRequest.class))).thenReturn(glossary);

            mockMvc.perform(post("/user/glossaries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceWord\":\"Hello\",\"targetWord\":\"你好\",\"remark\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        void 原词为空返回400() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/user/glossaries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceWord\":\"\",\"targetWord\":\"你好\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 译词为空返回400() throws Exception {
            setupSecurityContext();

            mockMvc.perform(post("/user/glossaries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceWord\":\"Hello\",\"targetWord\":\"\"}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("更新术语项")
    class UpdateGlossaryItemTests {

        @Test
        void 更新术语项成功() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.updateGlossaryItem(eq(1L), eq(1L), any(GlossaryItemRequest.class))).thenReturn(glossary);

            mockMvc.perform(put("/user/glossaries/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceWord\":\"Hi\",\"targetWord\":\"嗨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 术语项不存在返回错误() throws Exception {
            setupSecurityContext();
            when(userService.updateGlossaryItem(eq(1L), eq(999L), any(GlossaryItemRequest.class))).thenReturn(null);

            mockMvc.perform(put("/user/glossaries/999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sourceWord\":\"Hi\",\"targetWord\":\"嗨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("术语项不存在"));
        }
    }

    @Nested
    @DisplayName("删除术语项")
    class DeleteGlossaryItemTests {

        @Test
        void 删除术语项成功() throws Exception {
            setupSecurityContext();
            when(userService.deleteGlossaryItem(1L, 1L)).thenReturn(true);

            mockMvc.perform(delete("/user/glossaries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 术语项不存在返回错误() throws Exception {
            setupSecurityContext();
            when(userService.deleteGlossaryItem(1L, 999L)).thenReturn(false);

            mockMvc.perform(delete("/user/glossaries/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("术语项不存在"));
        }
    }

    @Nested
    @DisplayName("获取术语列表")
    class GetGlossaryTermsTests {

        @Test
        void 获取术语列表成功() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.getGlossaryDetail(1L, 1L)).thenReturn(glossary);
            when(userService.getGlossaryTerms(1L)).thenReturn(List.of(glossary));

            mockMvc.perform(get("/user/glossaries/1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void 术语库不存在返回错误() throws Exception {
            setupSecurityContext();
            when(userService.getGlossaryDetail(1L, 999L)).thenReturn(null);

            mockMvc.perform(get("/user/glossaries/999/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("术语库不存在"));
        }
    }

    @Nested
    @DisplayName("导出术语表为 CSV")
    class ExportGlossaryTests {

        @Test
        void 导出CSV成功() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.getGlossaryTerms(1L)).thenReturn(List.of(glossary));

            mockMvc.perform(get("/user/glossaries/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"glossary.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
        }

        @Test
        void 导出空列表也返回CSV() throws Exception {
            setupSecurityContext();
            when(userService.getGlossaryTerms(1L)).thenReturn(List.of());

            mockMvc.perform(get("/user/glossaries/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"glossary.csv\""));
        }

        @Test
        void 导出CSV包含BOM头() throws Exception {
            setupSecurityContext();
            GlossaryResponse glossary = createTestGlossary();
            when(userService.getGlossaryTerms(1L)).thenReturn(List.of(glossary));

            mockMvc.perform(get("/user/glossaries/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("﻿")));
        }
    }

    @Nested
    @DisplayName("导入术语表（CSV 上传）")
    class ImportGlossaryTests {

        @Test
        void 导入CSV文件成功() throws Exception {
            setupSecurityContext();
            String csvContent = "source_word,target_word,remark\nHello,你好,问候词";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "glossary.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            when(glossaryMapper.insert(any(Glossary.class))).thenReturn(1);

            mockMvc.perform(multipart("/user/glossaries/import")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1));
        }

        @Test
        void 空文件返回错误() throws Exception {
            setupSecurityContext();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "glossary.csv", "text/csv", new byte[0]);

            mockMvc.perform(multipart("/user/glossaries/import")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void 非CSV文件返回错误() throws Exception {
            setupSecurityContext();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "glossary.txt", "text/plain", "not a csv".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/user/glossaries/import")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("仅支持 CSV 文件"));
        }

        @Test
        void 导入多行CSV成功() throws Exception {
            setupSecurityContext();
            String csvContent = "source_word,target_word,remark\n"
                    + "Hello,你好,问候词\n"
                    + "World,世界,名词";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "glossary.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            when(glossaryMapper.insert(any(Glossary.class))).thenReturn(1);

            mockMvc.perform(multipart("/user/glossaries/import")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(2));
        }

        @Test
        void 跳过空行() throws Exception {
            setupSecurityContext();
            String csvContent = "source_word,target_word,remark\nHello,你好,\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "glossary.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            when(glossaryMapper.insert(any(Glossary.class))).thenReturn(1);

            mockMvc.perform(multipart("/user/glossaries/import")
                    .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
