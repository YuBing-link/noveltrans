package com.yumu.noveltranslator.service.state;

import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import org.springframework.stereotype.Component;

/**
 * 协作翻译状态机
 * 验证项目和章节任务的状态转移是否合法
 */
@Component
public class CollabStateMachine {

    /**
     * 验证项目状态转移是否合法
     */
    public void validateProjectTransition(CollabProjectStatus current, CollabProjectStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid project state transition: " + current + " → " + target);
        }
    }

    /**
     * 验证章节任务状态转移是否合法
     */
    public void validateChapterTransition(ChapterTaskStatus current, ChapterTaskStatus target) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid chapter task state transition: " + current + " → " + target);
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
}
