package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.CollabComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollabCommentMapper extends BaseMapper<CollabComment> {

    @Select("SELECT * FROM collab_comment WHERE chapter_task_id = #{chapterTaskId} AND parent_id IS NULL AND deleted = 0 ORDER BY create_time ASC")
    List<CollabComment> selectByChapterTaskId(@Param("chapterTaskId") Long chapterTaskId);

    @Select("SELECT * FROM collab_comment WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY create_time ASC")
    List<CollabComment> selectRepliesByParentId(@Param("parentId") Long parentId);
}
