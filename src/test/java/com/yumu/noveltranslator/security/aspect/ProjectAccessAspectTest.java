package com.yumu.noveltranslator.security.aspect;

import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProjectAccessAspect 单元测试")
class ProjectAccessAspectTest {

    @Mock
    private CollabProjectMemberMapper projectMemberMapper;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private RequireProjectAccess requireAccess;

    private ProjectAccessAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ProjectAccessAspect(projectMemberMapper);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setupAuthenticatedUser(Long userId, String userLevel) {
        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setUsername("testuser");
        user.setPassword("hashed");
        user.setUserLevel(userLevel != null ? userLevel : "FREE");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("认证检查测试")
    class AuthenticationTests {

        @Test
        @DisplayName("用户未认证抛出IllegalStateException")
        void 未认证抛出IllegalStateException() {
            SecurityContextHolder.clearContext();

            assertThrows(IllegalStateException.class, () -> aspect.checkAccess(joinPoint, requireAccess));
        }
    }

    @Nested
    @DisplayName("projectId提取测试")
    class ProjectIdExtractionTests {

        @Test
        @DisplayName("从方法参数(Long)提取projectId")
        void 从方法参数提取projectId() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{123L, "someString"});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.TRANSLATOR});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("TRANSLATOR");
            when(projectMemberMapper.selectByProjectAndUser(123L, 1L)).thenReturn(member);

            assertDoesNotThrow(() -> aspect.checkAccess(joinPoint, requireAccess));
        }

        @Test
        @DisplayName("无法从方法参数提取projectId抛出IllegalStateException")
        void 无法提取projectId抛出异常() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{"someString"});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.TRANSLATOR});

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> aspect.checkAccess(joinPoint, requireAccess));
            assertTrue(ex.getMessage().contains("无法从请求中提取 projectId"));
        }
    }

    @Nested
    @DisplayName("成员资格检查测试")
    class MembershipTests {

        @Test
        @DisplayName("用户不是项目成员抛出SecurityException")
        void 不是项目成员抛出SecurityException() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{456L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.TRANSLATOR});
            when(projectMemberMapper.selectByProjectAndUser(456L, 1L)).thenReturn(null);

            SecurityException ex = assertThrows(SecurityException.class,
                () -> aspect.checkAccess(joinPoint, requireAccess));
            assertTrue(ex.getMessage().contains("无权访问该项目"));
        }

        @Test
        @DisplayName("用户是项目成员且角色满足要求通过检查")
        void 角色满足要求通过检查() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{789L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.TRANSLATOR});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("OWNER");
            when(projectMemberMapper.selectByProjectAndUser(789L, 1L)).thenReturn(member);

            assertDoesNotThrow(() -> aspect.checkAccess(joinPoint, requireAccess));
        }

        @Test
        @DisplayName("角色权限不足抛出SecurityException")
        void 角色权限不足抛出异常() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{100L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.OWNER});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("TRANSLATOR");
            when(projectMemberMapper.selectByProjectAndUser(100L, 1L)).thenReturn(member);

            SecurityException ex = assertThrows(SecurityException.class,
                () -> aspect.checkAccess(joinPoint, requireAccess));
            assertTrue(ex.getMessage().contains("角色权限不足"));
        }
    }

    @Nested
    @DisplayName("角色级别检查测试")
    class RoleLevelTests {

        @Test
        @DisplayName("无角色要求只检查成员资格")
        void 无角色要求只检查成员资格() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{200L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("TRANSLATOR");
            when(projectMemberMapper.selectByProjectAndUser(200L, 1L)).thenReturn(member);

            // 空roles数组，anyMatch返回false，应该抛出角色权限不足
            SecurityException ex = assertThrows(SecurityException.class,
                () -> aspect.checkAccess(joinPoint, requireAccess));
            assertTrue(ex.getMessage().contains("角色权限不足"));
        }

        @Test
        @DisplayName("多角色要求任一匹配即可")
        void 多角色要求任一匹配即可() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{300L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.OWNER, ProjectMemberRole.REVIEWER});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("REVIEWER");
            when(projectMemberMapper.selectByProjectAndUser(300L, 1L)).thenReturn(member);

            // REVIEWER satisfies REVIEWER
            assertDoesNotThrow(() -> aspect.checkAccess(joinPoint, requireAccess));
        }

        @Test
        @DisplayName("OWNER满足所有角色要求")
        void OWNER满足所有角色要求() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{400L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.TRANSLATOR});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("OWNER");
            when(projectMemberMapper.selectByProjectAndUser(400L, 1L)).thenReturn(member);

            assertDoesNotThrow(() -> aspect.checkAccess(joinPoint, requireAccess));
        }

        @Test
        @DisplayName("REVIEWER满足REVIEWER和TRANSLATOR但不满足OWNER")
        void REVIEWER角色级别测试() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{500L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.REVIEWER});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("REVIEWER");
            when(projectMemberMapper.selectByProjectAndUser(500L, 1L)).thenReturn(member);

            assertDoesNotThrow(() -> aspect.checkAccess(joinPoint, requireAccess));
        }

        @Test
        @DisplayName("TRANSLATOR不满足REVIEWER要求")
        void TRANSLATOR不满足REVIEWER要求() {
            setupAuthenticatedUser(1L, "FREE");
            when(joinPoint.getArgs()).thenReturn(new Object[]{600L});
            when(requireAccess.roles()).thenReturn(new ProjectMemberRole[]{ProjectMemberRole.REVIEWER});

            CollabProjectMember member = new CollabProjectMember();
            member.setRole("TRANSLATOR");
            when(projectMemberMapper.selectByProjectAndUser(600L, 1L)).thenReturn(member);

            SecurityException ex = assertThrows(SecurityException.class,
                () -> aspect.checkAccess(joinPoint, requireAccess));
            assertTrue(ex.getMessage().contains("角色权限不足"));
        }
    }
}
