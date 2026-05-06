package com.yumu.noveltranslator.adapter.in.rest.collab;
import com.yumu.noveltranslator.dto.collab.CollabProjectResponse;
import com.yumu.noveltranslator.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.dto.common.PageResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.common.*;
import com.yumu.noveltranslator.dto.collab.*;
import com.yumu.noveltranslator.dto.entity.*;
import com.yumu.noveltranslator.dto.translation.*;
import com.yumu.noveltranslator.dto.subscription.*;
import com.yumu.noveltranslator.dto.auth.*;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
import com.yumu.noveltranslator.domain.service.CollabProjectService;
import com.yumu.noveltranslator.domain.service.ChapterTaskService;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
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

    @Mock
    private CollabProjectService collabProjectService;

    @Mock
    private ChapterTaskService chapterTaskService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private CollabProjectController controller;

    @BeforeEach
    void setUp() {
        SseEmitterUtil sseEmitterUtil = new SseEmitterUtil(stringRedisTemplate);
        controller = new CollabProjectController(collabProjectService, chapterTaskService, sseEmitterUtil);
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
            CreateCollabProjectRequest request = new CreateCollabProjectRequest();
            request.setName("测试项目");
            request.setDescription("描述");
            request.setSourceLang("en");
            request.setTargetLang("zh");

            CollabProjectResponse response = new CollabProjectResponse();
            response.setId(1L);
            response.setName("测试项目");
            response.setDescription("描述");
            response.setSourceLang("en");
            response.setTargetLang("zh");
            when(collabProjectService.createProject(any(), anyLong())).thenReturn(response);

            mockMvc.perform(post("/v1/collab/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Nested
    @DisplayName("获取项目列表")
    class ListProjectsTests {

        @Test
        void 返回分页数据() throws Exception {
            setupSecurityContext();
            PageResponse<CollabProjectResponse> pageResponse = new PageResponse<>();
            pageResponse.setPage(1);
            pageResponse.setPageSize(20);
            pageResponse.setTotal(0L);
            pageResponse.setTotalPages(0);
            pageResponse.setList(Collections.emptyList());
            when(collabProjectService.listByUserId(anyLong(), anyInt(), anyInt()))
                    .thenReturn(pageResponse);

            mockMvc.perform(get("/v1/collab/projects")
                    .param("page", "1")
                    .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());
        }
    }

    @Nested
    @DisplayName("获取项目详情")
    class GetProjectTests {

        @Test
        void 返回项目详情() throws Exception {
            setupSecurityContext();
            CollabProjectResponse response = new CollabProjectResponse();
            response.setId(1L);
            response.setName("项目");
            when(collabProjectService.getProjectById(1L)).thenReturn(response);

            mockMvc.perform(get("/v1/collab/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
        }
    }
}
