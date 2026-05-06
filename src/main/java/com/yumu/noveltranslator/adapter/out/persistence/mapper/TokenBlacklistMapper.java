package com.yumu.noveltranslator.adapter.out.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TokenBlacklist;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Data access interface for token_blacklist table. */
@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {

    @Select("SELECT * FROM token_blacklist WHERE token = #{token}")
    TokenBlacklist findByToken(String token);

    @Select("SELECT * FROM token_blacklist WHERE email = #{email}")
    List<TokenBlacklist> findByEmail(String email);

    @Delete("DELETE FROM token_blacklist WHERE expires_at < NOW()")
    void deleteExpired();

    @Delete("DELETE FROM token_blacklist WHERE email = #{email}")
    void deleteByUserEmail(String email);

    @Select("SELECT 1 FROM token_blacklist WHERE email = #{email} " +
            "AND (token IS NULL OR token = '') AND expires_at > NOW() LIMIT 1")
    Integer isEmailBlacklisted(String email);

    @Insert("INSERT INTO token_blacklist (email, reason, expires_at, created_at) " +
            "VALUES (#{email}, #{reason}, #{expiresAt}, #{createdAt})")
    void insertEmailBlacklist(@Param("email") String email,
                              @Param("reason") String reason,
                              @Param("expiresAt") java.time.LocalDateTime expiresAt,
                              @Param("createdAt") java.time.LocalDateTime createdAt);
}
