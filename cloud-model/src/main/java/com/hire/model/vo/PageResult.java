package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果包装。
 *
 * @param <T> 列表元素类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /** 当前页数据 */
    private List<T> list;
}
