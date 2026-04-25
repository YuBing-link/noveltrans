package com.yumu.noveltranslator.security.aspect;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * 项目访问权限 AOP 切面
 * 拦截 @RequireProjectAccess 注解，验证当前用户在指定项目中的角色是否满足要求
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ProjectAccessAspect {

    private final CollabProjectMemberMapper projectMemberMapper;

    @Before("@annotation(requireAccess)")
    public void checkAccess(JoinPoint joinPoint, RequireProjectAccess requireAccess) {
        Long userId = SecurityUtil.getRequiredUserId();

        Long projectId = extractProjectId(joinPoint);
        if (projectId == null) {
            throw new IllegalStateException("无法从请求中提取 projectId");
        }

        // 绕过租户过滤：项目成员关系独立于当前租户上下文查询
        CollabProjectMember member;
        try {
            TenantContext.setBypassTenant(true);
            member = projectMemberMapper.selectByProjectAndUser(projectId, userId);
        } finally {
            TenantContext.setBypassTenant(false);
        }
        if (member == null) {
            log.warn("用户 {} 无权访问项目 {}", userId, projectId);
            throw new SecurityException("无权访问该项目");
        }

        ProjectMemberRole userRole = ProjectMemberRole.fromValue(member.getRole());
        ProjectMemberRole[] requiredRoles = requireAccess.roles();
        boolean hasAccess = Arrays.stream(requiredRoles)
                .anyMatch(userRole::satisfies);

        if (!hasAccess) {
            log.warn("用户 {} 角色 {} 不满足要求 {}", userId, userRole, Arrays.toString(requiredRoles));
            throw new SecurityException("角色权限不足");
        }
    }

    /**
     * 从方法参数或请求路径中提取 projectId
     */
    private Long extractProjectId(JoinPoint joinPoint) {
        // 优先从方法参数中提取
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Long longVal) {
                return longVal;
            }
        }

        // 从 URL 路径参数提取
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String path = request.getRequestURI();
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("projects".equals(segments[i]) && i + 1 < segments.length) {
                    try {
                        return Long.parseLong(segments[i + 1]);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }
        return null;
    }
}
