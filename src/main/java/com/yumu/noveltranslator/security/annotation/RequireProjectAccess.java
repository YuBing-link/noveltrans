package com.yumu.noveltranslator.security.annotation;

import com.yumu.noveltranslator.enums.ProjectMemberRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 项目级访问权限注解
 * 用于 Controller 方法，验证当前用户是否具有指定项目角色的访问权限
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectAccess {
    ProjectMemberRole[] roles() default {ProjectMemberRole.OWNER, ProjectMemberRole.REVIEWER, ProjectMemberRole.TRANSLATOR};
}
