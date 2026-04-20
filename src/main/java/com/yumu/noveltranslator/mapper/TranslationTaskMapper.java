package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TranslationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TranslationTaskMapper extends BaseMapper<TranslationTask> {

    @Select("SELECT * FROM translation_task WHERE task_id = #{taskId} AND deleted = 0")
    TranslationTask findByTaskId(String taskId);

    @Select("SELECT * FROM translation_task WHERE document_id = #{docId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    TranslationTask findByDocumentId(Long docId);

    @Select("SELECT * FROM translation_task WHERE user_id = #{userId} AND status IN ('pending', 'processing') AND deleted = 0 ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<TranslationTask> findByUserIdAndStatus(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM translation_task WHERE status = #{status} AND create_time < #{cutoff} AND deleted = 0")
    List<TranslationTask> findByStatusAndCreateTimeBefore(@Param("status") String status, @Param("cutoff") LocalDateTime cutoff);
}
