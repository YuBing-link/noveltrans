package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.QuotaUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface QuotaUsageMapper extends BaseMapper<QuotaUsage> {

    @Select("SELECT * FROM quota_usage WHERE user_id = #{userId} AND usage_date = #{date}")
    QuotaUsage findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Update("INSERT INTO quota_usage (user_id, usage_date, characters_used) VALUES (#{userId}, #{date}, #{chars}) " +
            "ON DUPLICATE KEY UPDATE characters_used = characters_used + #{chars}, updated_at = NOW()")
    void incrementUsage(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("chars") long chars);

    @Update("INSERT INTO quota_usage (user_id, usage_date, characters_used) VALUES (#{userId}, #{date}, 0) " +
            "ON DUPLICATE KEY UPDATE characters_used = GREATEST(0, characters_used - #{chars}), updated_at = NOW()")
    void decrementUsage(@Param("userId") Long userId, @Param("date") LocalDate date, @Param("chars") long chars);

    @Select("SELECT COALESCE(SUM(characters_used), 0) FROM quota_usage WHERE user_id = #{userId} AND YEAR(usage_date) = YEAR(#{monthStart}) AND MONTH(usage_date) = MONTH(#{monthStart})")
    long getMonthlyUsage(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart);
}
