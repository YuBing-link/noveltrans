package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE email = #{email} AND password = #{password}" )
    User findByEmailAndPassword(String email, String password);

    @Select("SELECT * FROM user WHERE email = #{email}")
    User findByEmail(String email);

    @Select("SELECT COUNT(*) FROM user WHERE deleted = 0")
    int countActiveUsers();
}
