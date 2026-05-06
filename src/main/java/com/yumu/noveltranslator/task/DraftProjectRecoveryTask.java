package com.yumu.noveltranslator.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 恢复停滞在 DRAFT 状态的协作项目。
 * 每 5 分钟运行一次，检测 DRAFT 状态超过阈值的项目：
 * - 如果已有章节：说明异步插入已完成但激活失败，将其转为 ACTIVE
 * - 如果无章节：说明异步任务未执行或失败，记录为陈旧项目
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DraftProjectRecoveryTask {

    /**
     * DRAFT 项目被视为停滞的时间阈值（分钟）
     */
    private static final int STALE_THRESHOLD_MINUTES = 10;

    private final CollabProjectMapper collabProjectMapper;
    private final CollabChapterTaskMapper collabChapterTaskMapper;
    private final CollabStateMachine collabStateMachine;

    @Scheduled(fixedRate = 300_000)
    public void recoverStaleDraftProjects() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);

        // 查询 DRAFT 状态且创建时间早于阈值的项目
        List<CollabProject> staleDrafts = collabProjectMapper.selectList(
                new QueryWrapper<CollabProject>()
                        .eq("status", CollabProjectStatus.DRAFT.getValue())
                        .eq("deleted", 0)
                        .lt("create_time", cutoff)
        );

        if (staleDrafts.isEmpty()) {
            return;
        }

        log.info("发现 {} 个停滞的 DRAFT 项目，开始恢复", staleDrafts.size());

        for (CollabProject project : staleDrafts) {
            try {
                recoverSingleProject(project);
            } catch (Exception e) {
                log.error("恢复项目失败: projectId={}, error={}", project.getId(), e.getMessage(), e);
            }
        }
    }

    private void recoverSingleProject(CollabProject project) {
        Long projectId = project.getId();

        // 检查该项目是否已有章节
        int chapterCount = collabChapterTaskMapper.countByProjectId(projectId);

        if (chapterCount > 0) {
            // 有章节存在：异步任务可能已完成但激活失败
            transitionToActive(project);
        } else {
            // 无章节：异步任务未执行或完全失败
            logStaleProject(project);
        }
    }

    private void transitionToActive(CollabProject project) {
        Long projectId = project.getId();
        try {
            collabStateMachine.transitionProject(project, CollabProjectStatus.ACTIVE);
            collabProjectMapper.updateById(project);
            log.info("恢复停滞项目（有章节）: projectId={}, chapters exist, transitioned to ACTIVE", projectId);
        } catch (IllegalStateException e) {
            // 状态转移不合法（如已是 ACTIVE），跳过
            log.warn("无法转换项目状态: projectId={}, reason={}", projectId, e.getMessage());
        }
    }

    private void logStaleProject(CollabProject project) {
        Long projectId = project.getId();
        log.warn("发现陈旧 DRAFT 项目（无章节插入）: projectId={}, name={}, ownerId={}, createTime={}. "
                + "可能原因：异步章节拆分任务未执行或事件发布失败。"
                + "保持 DRAFT 状态，等待人工介入。",
                projectId, project.getName(), project.getOwnerId(), project.getCreateTime());
    }
}
