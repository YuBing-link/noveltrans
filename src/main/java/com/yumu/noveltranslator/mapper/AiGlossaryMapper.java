package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.AiGlossary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiGlossaryMapper extends BaseMapper<AiGlossary> {

    /**
     * 查询项目所有术语
     */
    @Select("SELECT * FROM ai_glossary WHERE project_id = #{projectId}")
    List<AiGlossary> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 按项目 ID 和状态查询术语
     */
    @Select("SELECT * FROM ai_glossary WHERE project_id = #{projectId} AND status = #{status}")
    List<AiGlossary> selectByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") String status);

    /**
     * 按章节 ID 查询术语
     */
    @Select("SELECT * FROM ai_glossary WHERE chapter_id = #{chapterId}")
    List<AiGlossary> selectByChapterId(@Param("chapterId") Long chapterId);
}
