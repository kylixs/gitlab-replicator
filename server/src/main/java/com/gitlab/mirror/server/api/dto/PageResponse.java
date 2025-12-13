package com.gitlab.mirror.server.api.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * Paginated Response
 *
 * @author GitLab Mirror Team
 */
@Data
public class PageResponse<T> {

    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;

    /**
     * Create from MyBatis-Plus IPage
     */
    public static <T> PageResponse<T> of(IPage<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setItems(page.getRecords());
        response.setTotal(page.getTotal());
        response.setPage((int) page.getCurrent());
        response.setPageSize((int) page.getSize());
        response.setTotalPages((int) page.getPages());
        return response;
    }

    /**
     * Create from list and pagination info
     */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setItems(items);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        return response;
    }
}
