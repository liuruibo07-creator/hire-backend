package com.hire.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 职位类别展示对象(树形结构,API设计文档 3.7)
 */
@Data
public class JobCategoryVO {

    private Long id;

    private Long parentId;

    private String name;

    private Integer sortOrder;

    private Integer level;

    /** 子分类列表 */
    private List<JobCategoryVO> children;
}