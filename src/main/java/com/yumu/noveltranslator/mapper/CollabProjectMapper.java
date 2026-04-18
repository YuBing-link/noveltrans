package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.CollabProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollabProjectMapper extends BaseMapper<CollabProject> {

    @Select("SELECT * FROM collab_project WHERE owner_id = #{ownerId} AND deleted = 0")
    List<CollabProject> selectByOwnerId(@Param("ownerId") Long ownerId);

    @Select("SELECT cp.* FROM collab_project cp " +
            "INNER JOIN collab_project_member cpm ON cp.id = cpm.project_id " +
            "WHERE cpm.user_id = #{userId} AND cp.deleted = 0 AND cpm.deleted = 0")
    List<CollabProject> selectByMemberUserId(@Param("userId") Long userId);
}
