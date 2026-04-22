package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.ChapterEntityMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChapterEntityMapMapper extends BaseMapper<ChapterEntityMap> {

    @Select("SELECT * FROM chapter_entity_map WHERE chapter_id = #{chapterId}")
    List<ChapterEntityMap> selectByChapterId(@Param("chapterId") Long chapterId);

    @Select("SELECT * FROM chapter_entity_map WHERE project_id = #{projectId}")
    List<ChapterEntityMap> selectByProjectId(@Param("projectId") Long projectId);
}
