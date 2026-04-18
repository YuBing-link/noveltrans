package com.yumu.noveltranslator.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 术语表实体
 */
@Data
@TableName("glossary")
public class Glossary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的用户 ID
     */
    private Long userId;

    /**
     * 待替换的原词
     */
    private String sourceWord;

    /**
     * 替换后的译词
     */
    private String targetWord;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;
}
