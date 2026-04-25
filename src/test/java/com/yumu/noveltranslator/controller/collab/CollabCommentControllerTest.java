package com.yumu.noveltranslator.controller.collab;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.CommentResponse;
import com.yumu.noveltranslator.dto.CreateCommentRequest;
import com.yumu.noveltranslator.dto.PageResponse;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.CollabCommentService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollabCommentController 测试")
class CollabCommentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private CollabCommentService collabCommentService;

    private CollabCommentController controller;

    @BeforeEach
    void setUp() {
        controller = new CollabCommentController(collabCommentService);
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

    private CommentResponse createCommentResponse() {
        CommentResponse resp = new CommentResponse();
        resp.setId(1L);
        resp.setUserId(1L);
        resp.setContent("评论内容");
        resp.setResolved(false);
        resp.setCreateTime(LocalDateTime.now());
        return resp;
    }

    @Nested
    @DisplayName("创建评论")
    class CreateCommentTests {

        @Test
        void 创建评论成功() throws Exception {
            setupSecurityContext();
            CommentResponse resp = createCommentResponse();

            CreateCommentRequest req = new CreateCommentRequest();
            req.setContent("评论内容");

            when(collabCommentService.createComment(eq(1L), eq(1L), any())).thenReturn(resp);

            mockMvc.perform(post("/v1/collab/chapters/1/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("评论内容"));
        }
    }

    @Nested
    @DisplayName("列出评论")
    class ListCommentsTests {

        @Test
        void 列出章节评论() throws Exception {
            setupSecurityContext();
            CommentResponse resp = createCommentResponse();

            Page<CommentResponse> iPage = new Page<>();
            iPage.setCurrent(1);
            iPage.setSize(20);
            iPage.setTotal(1);
            iPage.setRecords(List.of(resp));
            when(collabCommentService.getCommentsByChapterPage(1L, 1L, 1, 20)).thenReturn(iPage);

            mockMvc.perform(get("/v1/collab/chapters/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list[0].content").value("评论内容"));
        }
    }

    @Nested
    @DisplayName("解决评论")
    class ResolveCommentTests {

        @Test
        void 解决评论成功() throws Exception {
            setupSecurityContext();
            doNothing().when(collabCommentService).resolveComment(1L, 1L);

            mockMvc.perform(put("/v1/collab/comments/1/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("删除评论")
    class DeleteCommentTests {

        @Test
        void 删除评论成功() throws Exception {
            setupSecurityContext();
            doNothing().when(collabCommentService).deleteComment(1L, 1L);

            mockMvc.perform(delete("/v1/collab/comments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
