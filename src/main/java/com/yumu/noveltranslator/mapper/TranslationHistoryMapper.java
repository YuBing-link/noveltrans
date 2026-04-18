package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TranslationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TranslationHistoryMapper extends BaseMapper<TranslationHistory> {

    @Select("SELECT * FROM translation_history WHERE task_id = #{taskId} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    TranslationHistory findByTaskId(String taskId);

    @Select("SELECT * FROM translation_history WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC LIMIT #{offset}, #{pageSize}")
    List<TranslationHistory> findByUserId(Long userId, int offset, int pageSize);

    @Select("SELECT COUNT(*) FROM translation_history WHERE user_id = #{userId} AND deleted = 0")
    int countByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM translation_history WHERE user_id = #{userId} AND deleted = 0 AND type = #{type}")
    int countByUserIdAndType(Long userId, String type);

    @Select("SELECT SUM(LENGTH(source_text)) FROM translation_history WHERE user_id = #{userId} AND deleted = 0")
    Long sumSourceTextLengthByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM translation_history WHERE user_id = #{userId} AND deleted = 0 AND create_time >= #{startTime}")
    int countByUserIdAfter(Long userId, LocalDateTime startTime);

    @Select("SELECT COUNT(*) FROM translation_history WHERE deleted = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM translation_history WHERE deleted = 0 AND create_time >= #{startTime}")
    long countAfter(LocalDateTime startTime);

    @Select("SELECT COUNT(DISTINCT user_id) FROM translation_history WHERE deleted = 0 AND create_time >= #{startTime}")
    int countActiveUsersAfter(LocalDateTime startTime);

    @Select("SELECT COALESCE(SUM(LENGTH(source_text)), 0) FROM translation_history WHERE deleted = 0")
    long sumAllSourceTextLength();

    @Select("SELECT COUNT(*) FROM translation_history WHERE deleted = 0 AND type = 'document'")
    int countDocumentTranslations();
}
