package com.hire.job.mapper;

import com.hire.model.entity.JobCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 职位类别表 Mapper,对应 cloud_job.t_job_category
 */
public interface JobCategoryMapper {

    /** 查询所有启用的类别(按层级、排序号排序) */
    List<JobCategory> selectAllEnabled();

    /** 根据ID查询(未删除且启用) */
    JobCategory selectById(@Param("id") Long id);
}