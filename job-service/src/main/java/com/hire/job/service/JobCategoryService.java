package com.hire.job.service;

import com.hire.model.vo.JobCategoryVO;

import java.util.List;

/**
 * 职位类别业务接口
 */
public interface JobCategoryService {

    /** 查询职位类别树形结构 */
    List<JobCategoryVO> listCategoryTree();

    /** 按ID查询类别名称(读分类缓存,不存在返回 null) */
    String getCategoryName(Long categoryId);
}