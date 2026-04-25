package com.yumu.noveltranslator.controller.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.CollabProjectService;
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
@DisplayName("CollabMemberController 测试")
class CollabMemberControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private CollabProjectService collabProjectService;

    private CollabMemberController controller;

    @BeforeEach
    void setUp() {
        controller = new CollabMemberController(collabProjectService);
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

    private ProjectMemberResponse createMemberResponse() {
        ProjectMemberResponse resp = new ProjectMemberResponse();
        resp.setId(1L);
        resp.setUserId(2L);
        resp.setUsername("testuser");
        resp.setRole("translator");
        return resp;
    }

    @Nested
    @DisplayName("邀请成员")
    class InviteMemberTests {

        @Test
        void 邀请成员成功() throws Exception {
            setupSecurityContext();
            ProjectMemberResponse resp = createMemberResponse();

            InviteMemberRequest req = new InviteMemberRequest();
            req.setRole(ProjectMemberRole.TRANSLATOR);

            when(collabProjectService.inviteMember(eq(1L), any(), eq(1L))).thenReturn(resp);

            mockMvc.perform(post("/v1/collab/projects/1/invite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("通过邀请码加入")
    class JoinByCodeTests {

        @Test
        void 通过邀请码加入成功() throws Exception {
            setupSecurityContext();
            ProjectMemberResponse resp = createMemberResponse();

            when(collabProjectService.joinByInviteCode(eq("ABC123"), eq(1L))).thenReturn(resp);

            mockMvc.perform(post("/v1/collab/join")
                    .param("inviteCode", "ABC123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("列出成员")
    class ListMembersTests {

        @Test
        void 列出项目成员() throws Exception {
            setupSecurityContext();
            ProjectMemberResponse resp = createMemberResponse();

            PageResponse<ProjectMemberResponse> pageResp = PageResponse.of(1, 20, 1L, List.of(resp));
            when(collabProjectService.getMembers(1L, 1, 20)).thenReturn(pageResp);

            mockMvc.perform(get("/v1/collab/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.list[0].username").value("testuser"));
        }
    }

    @Nested
    @DisplayName("移除成员")
    class RemoveMemberTests {

        @Test
        void 移除成员成功() throws Exception {
            setupSecurityContext();

            mockMvc.perform(delete("/v1/collab/projects/1/members/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }
    }
}
