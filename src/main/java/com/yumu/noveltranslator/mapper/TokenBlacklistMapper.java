package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TokenBlacklist;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {

    @Select("SELECT * FROM token_blacklist WHERE token = #{token}")
    TokenBlacklist findByToken(String token);

    @Select("SELECT * FROM token_blacklist WHERE email = #{email}")
    List<TokenBlacklist> findByEmail(String email);

    @Insert("INSERT INTO token_blacklist (token, email, reason, expires_at, created_at) " +
            "VALUES (#{token}, #{email}, #{reason}, #{expiresAt}, #{createdAt})")
    void insert(TokenBlacklist blacklist);

    @Delete("DELETE FROM token_blacklist WHERE expires_at < NOW()")
    void deleteExpired();

    @Delete("DELETE FROM token_blacklist WHERE email = #{email}")
    void deleteByUserEmail(String email);
}
