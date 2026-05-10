package com.yumu.noveltranslator.adapter.out.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Glossary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GlossaryMapper extends BaseMapper<Glossary> {

    /**
     * 查询用户所有术语（包含软删除记录），绕过 @TableLogic 过滤
     */
    @Select("SELECT * FROM glossary WHERE user_id = #{userId}")
    List<Glossary> selectAllByUserId(@Param("userId") Long userId);

    /**
     * 恢复软删除的术语（绕过 @TableLogic 拦截器）
     */
    @Update("UPDATE glossary SET deleted = 0 WHERE id = #{id}")
    int restoreDeleted(@Param("id") Long id);
}

