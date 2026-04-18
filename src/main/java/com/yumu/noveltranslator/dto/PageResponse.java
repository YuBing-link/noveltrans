package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;

/**
 * 分页响应
 */
@Data
public class PageResponse<T> {
    /**
     * 当前页码
     */
    private Integer page;
    /**
     * 每页数量
     */
    private Integer pageSize;
    /**
     * 总记录数
     */
    private Long total;
    /**
     * 总页数
     */
    private Integer totalPages;
    /**
     * 数据列表
     */
    private List<T> list;

    public static <T> PageResponse<T> of(Integer page, Integer pageSize, Long total, List<T> list) {
        PageResponse<T> response = new PageResponse<>();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        response.setList(list);
        return response;
    }
}
