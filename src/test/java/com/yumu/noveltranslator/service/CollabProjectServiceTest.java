package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.dto.CollabProjectResponse;
import com.yumu.noveltranslator.dto.PageResponse;
import com.yumu.noveltranslator.dto.ProjectMemberResponse;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.mapper.CollabInviteCodeMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.MultiAgentTranslationService;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabProjectServiceTest {

    @Mock
    private CollabProjectMapper collabProjectMapper;

    @Mock
    private CollabProjectMemberMapper collabProjectMemberMapper;

    @Mock
    private CollabChapterTaskMapper chapterTaskMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CollabCommentMapper collabCommentMapper;

    @Mock
    private CollabInviteCodeMapper collabInviteCodeMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private CollabStateMachine collabStateMachine;

    @Mock
    private MultiAgentTranslationService multiAgentTranslationService;

    private CollabProjectService collabProjectService;

    @BeforeEach
    void setUp() {
        collabProjectService = new CollabProjectService(
                collabProjectMapper, collabProjectMemberMapper, chapterTaskMapper, collabCommentMapper,
                collabInviteCodeMapper, documentMapper, userMapper, collabStateMachine, multiAgentTranslationService);
    }

    @Nested
    @DisplayName("用户参与的项目列表")
    class ListByUserIdTests {

        @Test
        void 返回第一页数据() {
            CollabProject p1 = buildProject(1L, "项目A");
            CollabProject p2 = buildProject(2L, "项目B");

            when(collabProjectMapper.selectByMemberUserId(1L)).thenReturn(List.of(p1, p2));

            PageResponse<CollabProjectResponse> result = collabProjectService.listByUserId(1L, 1, 10);

            assertEquals(2, result.getList().size());
            assertEquals(2, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals("项目A", result.getList().get(0).getName());
        }

        @Test
        void 分页超出范围返回空列表() {
            CollabProject p = buildProject(1L, "项目A");

            when(collabProjectMapper.selectByMemberUserId(1L)).thenReturn(List.of(p));

            PageResponse<CollabProjectResponse> result = collabProjectService.listByUserId(1L, 3, 10);

            assertTrue(result.getList().isEmpty());
            assertEquals(1, result.getTotal());
        }

        @Test
        void 没有参与项目返回空列表() {
            when(collabProjectMapper.selectByMemberUserId(1L)).thenReturn(List.of());

            PageResponse<CollabProjectResponse> result = collabProjectService.listByUserId(1L, 1, 10);

            assertTrue(result.getList().isEmpty());
            assertEquals(0, result.getTotal());
        }

        @Test
        void 分页第二页返回剩余数据() {
            List<CollabProject> allProjects = List.of(
                    buildProject(1L, "项目1"),
                    buildProject(2L, "项目2"),
                    buildProject(3L, "项目3"),
                    buildProject(4L, "项目4"),
                    buildProject(5L, "项目5")
            );
            when(collabProjectMapper.selectByMemberUserId(1L)).thenReturn(allProjects);

            PageResponse<CollabProjectResponse> result = collabProjectService.listByUserId(1L, 2, 2);

            assertEquals(2, result.getList().size());
            assertEquals(5, result.getTotal());
            assertEquals("项目3", result.getList().get(0).getName());
            assertEquals("项目4", result.getList().get(1).getName());
        }

        private CollabProject buildProject(Long id, String name) {
            CollabProject p = new CollabProject();
            p.setId(id);
            p.setName(name);
            p.setDescription("desc");
            p.setSourceLang("en");
            p.setTargetLang("zh");
            p.setStatus(CollabProjectStatus.ACTIVE.getValue());
            p.setProgress(0);
            return p;
        }
    }

    @Nested
    @DisplayName("项目成员列表")
    class GetMembersTests {

        @Test
        void 返回第一页成员() {
            CollabProjectMember m1 = buildMember(1L, 1L, "owner");
            CollabProjectMember m2 = buildMember(2L, 2L, "translator");

            when(collabProjectMemberMapper.selectByProjectId(1L)).thenReturn(List.of(m1, m2));

            User u1 = new User();
            u1.setId(1L);
            u1.setUsername("张三");
            User u2 = new User();
            u2.setId(2L);
            u2.setUsername("李四");
            when(userMapper.selectById(1L)).thenReturn(u1);
            when(userMapper.selectById(2L)).thenReturn(u2);

            PageResponse<ProjectMemberResponse> result = collabProjectService.getMembers(1L, 1, 10);

            assertEquals(2, result.getList().size());
            assertEquals(2, result.getTotal());
            assertEquals("张三", result.getList().get(0).getUsername());
        }

        @Test
        void 分页超出范围返回空列表() {
            CollabProjectMember m = buildMember(1L, 1L, "owner");
            when(collabProjectMemberMapper.selectByProjectId(1L)).thenReturn(List.of(m));

            User u = new User();
            u.setId(1L);
            u.setUsername("张三");
            when(userMapper.selectById(1L)).thenReturn(u);

            PageResponse<ProjectMemberResponse> result = collabProjectService.getMembers(1L, 5, 10);

            assertTrue(result.getList().isEmpty());
            assertEquals(1, result.getTotal());
        }

        @Test
        void 没有成员返回空列表() {
            when(collabProjectMemberMapper.selectByProjectId(1L)).thenReturn(List.of());

            PageResponse<ProjectMemberResponse> result = collabProjectService.getMembers(1L, 1, 10);

            assertTrue(result.getList().isEmpty());
            assertEquals(0, result.getTotal());
        }

        private CollabProjectMember buildMember(Long id, Long userId, String role) {
            CollabProjectMember m = new CollabProjectMember();
            m.setId(id);
            m.setUserId(userId);
            m.setProjectId(1L);
            m.setRole(role);
            return m;
        }
    }
}
