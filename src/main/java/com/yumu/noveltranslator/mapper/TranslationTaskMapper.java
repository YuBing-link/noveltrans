package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TranslationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TranslationTaskMapper extends BaseMapper<TranslationTask> {

    @Select("SELECT * FROM translation_task WHERE task_id = #{taskId} AND deleted = 0")
    TranslationTask findByTaskId(String taskId);

    @Select("SELECT * FROM translation_task WHERE document_id = #{docId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    TranslationTask findByDocumentId(Long docId);
}
