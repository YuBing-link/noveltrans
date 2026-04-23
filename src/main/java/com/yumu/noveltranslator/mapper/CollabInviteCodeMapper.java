package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.CollabInviteCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CollabInviteCodeMapper extends BaseMapper<CollabInviteCode> {

    @Select("SELECT * FROM collab_invite_code WHERE code = #{code} AND deleted = 0 AND used = 0 AND expires_at > NOW()")
    CollabInviteCode selectByValidCode(@Param("code") String code);

    @Select("SELECT * FROM collab_invite_code WHERE code = #{code} AND deleted = 0")
    CollabInviteCode selectByCode(@Param("code") String code);

    @Update("UPDATE collab_invite_code SET used = 1 WHERE id = #{id}")
    int markAsUsed(@Param("id") Long id);
}
