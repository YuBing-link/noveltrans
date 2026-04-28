package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    @Select("SELECT id, tenant_id, user_id, api_key, name, is_active AS active, last_used_at, total_usage, created_at FROM api_keys WHERE api_key = #{apiKey}")
    ApiKey findByApiKey(@Param("apiKey") String apiKey);

    @Select("SELECT * FROM api_keys WHERE user_id = #{userId} AND is_active = 1 ORDER BY created_at DESC")
    List<ApiKey> findByUserId(@Param("userId") Long userId);

    @Update("UPDATE api_keys SET last_used_at = NOW(), total_usage = total_usage + #{usage} WHERE id = #{id}")
    void incrementUsage(@Param("id") Long id, @Param("usage") long usage);
}
