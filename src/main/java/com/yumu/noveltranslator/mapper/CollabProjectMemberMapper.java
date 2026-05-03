package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollabProjectMemberMapper extends BaseMapper<CollabProjectMember> {

    @Select("SELECT * FROM collab_project_member WHERE project_id = #{projectId} AND deleted = 0")
    List<CollabProjectMember> selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM collab_project_member WHERE invite_code = #{inviteCode} AND deleted = 0 AND invite_status = 'ACTIVE'")
    CollabProjectMember selectByInviteCode(@Param("inviteCode") String inviteCode);

    @Select("SELECT COUNT(*) FROM collab_project_member WHERE project_id = #{projectId} AND role = #{role} AND deleted = 0")
    int countByProjectIdAndRole(@Param("projectId") Long projectId, @Param("role") String role);

    @Select("SELECT * FROM collab_project_member WHERE project_id = #{projectId} AND user_id = #{userId} AND deleted = 0")
    CollabProjectMember selectByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM collab_project_member WHERE project_id = #{projectId} AND deleted = 0")
    int countByProjectId(@Param("projectId") Long projectId);
}
