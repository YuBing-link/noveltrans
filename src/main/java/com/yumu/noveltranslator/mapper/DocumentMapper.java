package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    @Select("SELECT * FROM document WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<Document> findByUserId(Long userId);

    @Select("SELECT * FROM document WHERE id = #{id} AND deleted = 0")
    Document findById(Long id);

    @Select("SELECT * FROM document WHERE user_id = #{userId} AND id = #{id} AND deleted = 0")
    Document findByIdAndUserId(Long id, Long userId);
}
