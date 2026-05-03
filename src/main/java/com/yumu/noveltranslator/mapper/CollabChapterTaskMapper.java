package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CollabChapterTaskMapper extends BaseMapper<CollabChapterTask> {

    @Select("SELECT * FROM collab_chapter_task WHERE project_id = #{projectId} AND deleted = 0 ORDER BY chapter_number ASC")
    List<CollabChapterTask> selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM collab_chapter_task WHERE project_id = #{projectId} AND status = #{status} AND deleted = 0")
    List<CollabChapterTask> selectByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM collab_chapter_task WHERE project_id = #{projectId} AND status = #{status} AND deleted = 0")
    int countByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

    @Select("SELECT * FROM collab_chapter_task WHERE assignee_id = #{assigneeId} AND deleted = 0 AND status IN ('TRANSLATING', 'SUBMITTED')")
    List<CollabChapterTask> selectByAssigneeId(@Param("assigneeId") Long assigneeId);

    @Select("SELECT * FROM collab_chapter_task WHERE status = #{status} AND update_time < #{cutoff} AND deleted = 0 ORDER BY update_time ASC LIMIT 50")
    List<CollabChapterTask> findByStatusAndUpdateTimeBefore(
            @Param("status") String status, @Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE collab_chapter_task SET retry_count = #{retryCount}, update_time = NOW() WHERE id = #{id}")
    int updateRetryCount(@Param("id") Long id, @Param("retryCount") int retryCount);
}
