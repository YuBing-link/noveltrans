package com.yumu.noveltranslator.service.state;

import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import org.springframework.stereotype.Component;

/**
 * 协作翻译状态机
 * 验证项目和章节任务的状态转移是否合法，并提供驱动状态转移的方法。
 *
 * 状态转移应通过 {@link #transitionProject} / {@link #transitionChapter} 驱动，
 * 而非直接调用实体的 setStatus，以确保所有转移都经过合法性校验。
 */
@Component
public class CollabStateMachine {

    // ========== Validation-only methods (keep for backward compatibility) ==========

    /**
     * 验证项目状态转移是否合法
     */
    public void validateProjectTransition(CollabProjectStatus current, CollabProjectStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid project state transition: " + current + " -> " + target);
        }
    }

    /**
     * 验证章节任务状态转移是否合法
     */
    public void validateChapterTransition(ChapterTaskStatus current, ChapterTaskStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid chapter task state transition: " + current + " -> " + target);
        }
    }

    /**
     * 从字符串值验证项目状态转移
     */
    public void validateProjectTransition(String currentStr, String targetStr) {
        CollabProjectStatus current = CollabProjectStatus.fromValue(currentStr);
        CollabProjectStatus target = CollabProjectStatus.fromValue(targetStr);
        validateProjectTransition(current, target);
    }

    /**
     * 从字符串值验证章节状态转移
     */
    public void validateChapterTransition(String currentStr, String targetStr) {
        ChapterTaskStatus current = ChapterTaskStatus.fromValue(currentStr);
        ChapterTaskStatus target = ChapterTaskStatus.fromValue(targetStr);
        validateChapterTransition(current, target);
    }

    // ========== Driving methods (validate + set) ==========

    /**
     * 驱动项目状态转移：先校验转移合法性，再更新项目实体的 status 字段。
     * 这是修改 CollabProject 状态的唯一正确方式。
     *
     * @param project 项目实体
     * @param targetStatus 目标状态
     */
    public void transitionProject(CollabProject project, CollabProjectStatus targetStatus) {
        CollabProjectStatus current = CollabProjectStatus.fromValue(project.getStatus());
        validateProjectTransition(current, targetStatus);
        project.setStatus(targetStatus.getValue());
    }

    /**
     * 驱动项目状态转移（字符串重载）。
     */
    public void transitionProject(CollabProject project, String targetStatusStr) {
        CollabProjectStatus current = CollabProjectStatus.fromValue(project.getStatus());
        CollabProjectStatus target = CollabProjectStatus.fromValue(targetStatusStr);
        transitionProject(project, target);
    }

    /**
     * 驱动章节任务状态转移：先校验转移合法性，再更新章节实体的 status 字段。
     * 这是修改 CollabChapterTask 状态的唯一正确方式。
     *
     * @param chapter 章节任务实体
     * @param targetStatus 目标状态
     */
    public void transitionChapter(CollabChapterTask chapter, ChapterTaskStatus targetStatus) {
        ChapterTaskStatus current = ChapterTaskStatus.fromValue(chapter.getStatus());
        validateChapterTransition(current, targetStatus);
        chapter.setStatus(targetStatus.getValue());
    }

    /**
     * 驱动章节任务状态转移（字符串重载）。
     */
    public void transitionChapter(CollabChapterTask chapter, String targetStatusStr) {
        ChapterTaskStatus current = ChapterTaskStatus.fromValue(chapter.getStatus());
        ChapterTaskStatus target = ChapterTaskStatus.fromValue(targetStatusStr);
        transitionChapter(chapter, target);
    }
}
