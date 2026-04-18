package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {

    @Select("SELECT * FROM user_preferences WHERE user_id = #{userId}")
    UserPreference findByUserId(Long userId);
}
