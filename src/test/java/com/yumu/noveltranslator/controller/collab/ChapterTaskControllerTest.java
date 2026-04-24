package com.yumu.noveltranslator.controller.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.AssignChapterRequest;
import com.yumu.noveltranslator.dto.ChapterTaskResponse;
import com.yumu.noveltranslator.dto.ReviewChapterRequest;
import com.yumu.noveltranslator.dto.SubmitChapterRequest;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.ChapterTaskService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterTaskController 测试")
class ChapterTaskControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private ChapterTaskService chapterTaskService;

    private ChapterTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new ChapterTaskController(chapterTaskService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private ChapterTaskResponse createChapterResponse() {
        ChapterTaskResponse resp = new ChapterTaskResponse();
        resp.setId(1L);
        resp.setChapterNumber(1);
        resp.setTitle("第一章");
        resp.setSourceText("Hello");
        resp.setStatus("unassigned");
        resp.setProgress(0);
        resp.setSourceWordCount(5);
        return resp;
    }

    @Nested
    @DisplayName("分配章节")
    class AssignChapterTests {

        @Test
        void 分配章节成功() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse resp = createChapterResponse();
            resp.setAssigneeId(2L);

            when(chapterTaskService.assignChapter(eq(1L), eq(2L), eq(1L))).thenReturn(resp);

            AssignChapterRequest req = new AssignChapterRequest();
            req.setAssigneeId(2L);

            mockMvc.perform(put("/v1/collab/chapters/1/assign")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assigneeId").value(2));
        }
    }

    @Nested
    @DisplayName("提交章节")
    class SubmitChapterTests {

        @Test
        void 提交章节成功() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse resp = createChapterResponse();
            resp.setTranslatedText("翻译内容");

            when(chapterTaskService.submitChapter(eq(1L), eq("翻译内容"))).thenReturn(resp);

            SubmitChapterRequest req = new SubmitChapterRequest();
            req.setTranslatedText("翻译内容");

            mockMvc.perform(put("/v1/collab/chapters/1/submit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("审校章节")
    class ReviewChapterTests {

        @Test
        void 审核通过() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse resp = createChapterResponse();

            when(chapterTaskService.reviewChapter(eq(1L), eq(true), eq("很好"), eq(1L))).thenReturn(resp);

            ReviewChapterRequest req = new ReviewChapterRequest();
            req.setApproved(true);
            req.setComment("很好");

            mockMvc.perform(put("/v1/collab/chapters/1/review")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("获取章节详情")
    class GetChapterTests {

        @Test
        void 获取章节详情() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse resp = createChapterResponse();

            when(chapterTaskService.getChapterById(1L, 1L)).thenReturn(resp);

            mockMvc.perform(get("/v1/collab/chapters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("第一章"));
        }
    }

    @Nested
    @DisplayName("获取我的章节")
    class ListMyChaptersTests {

        @Test
        void 获取译员章节列表() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse resp = createChapterResponse();

            when(chapterTaskService.listByAssigneeId(1L)).thenReturn(List.of(resp));

            mockMvc.perform(get("/v1/collab/chapters/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
