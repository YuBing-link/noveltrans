package com.yumu.noveltranslator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.entity.TranslationCache;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 翻译缓存数据访问层
 */
@Mapper
public interface TranslationCacheMapper extends BaseMapper<TranslationCache> {

    /**
     * 自定义插入方法，使用 INSERT IGNORE 避免重复键冲突
     */
    @Insert("INSERT IGNORE INTO translation_cache (cache_key, source_text, target_text, source_lang, target_lang, engine, expire_time, create_time) " +
            "VALUES (#{cacheKey}, #{sourceText}, #{targetText}, #{sourceLang}, #{targetLang}, #{engine}, #{expireTime}, #{createTime})")
    int insertCache(TranslationCache cache);
}
