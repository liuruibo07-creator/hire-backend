package com.hire.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位类别实体,对应 cloud_job 库的 t_job_category 表
 * 支持三级分类,parent_id 自关联实现树形结构
 */
@Data
public class JobCategory {

    /** 类别ID,雪花算法生成 */
    private Long id;

    /** 父类别ID,0表示顶级分类 */
    private Long parentId;

    /** 类别名称 */
    private String name;

    /** 排序序号 */
    private Integer sortOrder;

    /** 层级:1-一级 / 2-二级 / 3-三级 */
    private Integer level;

    /** 状态:0-禁用 / 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除:0-未删除 / 1-已删除 */
    private Integer deleted;
}