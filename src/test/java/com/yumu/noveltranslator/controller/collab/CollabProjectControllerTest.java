package com.yumu.noveltranslator.controller.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.CollabProjectService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollabProjectController 测试")
class CollabProjectControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private CollabProjectService collabProjectService;

    @org.mockito.Mock
    private ChapterTaskService chapterTaskService;

    private CollabProjectController controller;

    @BeforeEach
    void setUp() {
        controller = new CollabProjectController(collabProjectService, chapterTaskService);
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

    @Nested
    @DisplayName("创建项目")
    class CreateProjectTests {

        @Test
        void 创建项目成功() throws Exception {
            setupSecurityContext();
            CollabProjectResponse resp = new CollabProjectResponse();
            resp.setId(1L);
            resp.setName("测试项目");

            CreateCollabProjectRequest req = new CreateCollabProjectRequest();
            req.setName("测试项目");
            req.setSourceLang("en");
            req.setTargetLang("zh");

            when(collabProjectService.createProject(any(), eq(1L))).thenReturn(resp);

            mockMvc.perform(post("/v1/collab/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Nested
    @DisplayName("列出项目")
    class ListProjectsTests {

        @Test
        void 列出用户的项目() throws Exception {
            setupSecurityContext();
            CollabProjectResponse resp = new CollabProjectResponse();
            resp.setId(1L);
            resp.setName("测试项目");

            when(collabProjectService.listByUserId(1L)).thenReturn(List.of(resp));

            mockMvc.perform(get("/v1/collab/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
        }
    }

    @Nested
    @DisplayName("列出章节")
    class ListChaptersTests {

        @Test
        void 列出项目章节() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse chapter = new ChapterTaskResponse();
            chapter.setId(1L);
            chapter.setTitle("第一章");

            when(chapterTaskService.listByProjectId(1L)).thenReturn(List.of(chapter));

            mockMvc.perform(get("/v1/collab/projects/1/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("第一章"));
        }
    }

    @Nested
    @DisplayName("获取项目详情")
    class GetProjectTests {

        @Test
        void 获取项目详情() throws Exception {
            setupSecurityContext();
            CollabProjectResponse resp = new CollabProjectResponse();
            resp.setId(1L);
            resp.setName("测试项目");

            when(collabProjectService.getProjectById(1L)).thenReturn(resp);

            mockMvc.perform(get("/v1/collab/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("测试项目"));
        }
    }

    @Nested
    @DisplayName("创建章节")
    class CreateChapterTests {

        @Test
        void 创建章节成功() throws Exception {
            setupSecurityContext();
            ChapterTaskResponse chapter = new ChapterTaskResponse();
            chapter.setId(1L);
            chapter.setTitle("第一章");

            when(chapterTaskService.createChapter(eq(1L), eq(1), eq("第一章"), eq("Hello"), eq(1L)))
                .thenReturn(chapter);

            mockMvc.perform(post("/v1/collab/projects/1/chapters")
                    .param("chapterNumber", "1")
                    .param("title", "第一章")
                    .param("sourceText", "Hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
