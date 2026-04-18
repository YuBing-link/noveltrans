package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TranslationMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TranslationMemoryMapper extends BaseMapper<TranslationMemory> {

    @Select("SELECT * FROM translation_memory WHERE user_id = #{userId} AND source_lang = #{sourceLang} AND target_lang = #{targetLang} AND deleted = 0 ORDER BY usage_count DESC LIMIT #{limit}")
    List<TranslationMemory> selectTopByUserAndLang(@Param("userId") Long userId, @Param("sourceLang") String sourceLang, @Param("targetLang") String targetLang, @Param("limit") int limit);

    @Select("SELECT * FROM translation_memory WHERE project_id = #{projectId} AND deleted = 0 ORDER BY usage_count DESC")
    List<TranslationMemory> selectByProjectId(@Param("projectId") Long projectId);
}
