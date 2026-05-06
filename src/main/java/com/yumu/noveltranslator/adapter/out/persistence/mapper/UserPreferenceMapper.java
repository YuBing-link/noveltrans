package com.yumu.noveltranslator.adapter.out.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {

    @Select("SELECT * FROM user_preferences WHERE user_id = #{userId}")
    UserPreference findByUserId(Long userId);
}
