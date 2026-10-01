package com.hire.model.dto;

import lombok.Data;

/**
 * 职位搜索请求参数(API设计文档 3.6)
 */
@Data
public class JobSearchDTO {

    /** 搜索关键词(IK分词) */
    private String keyword;

    /** 职位类型筛选 */
    private String jobType;

    /** 城市筛选 */
    private String city;

    /** 类别筛选 */
    private Long categoryId;

    /** 行业筛选 */
    private String industry;

    /** 经验要求筛选 */
    private String experienceReq;

    /** 学历要求筛选 */
    private String educationReq;

    /** 最低薪资筛选(K) */
    private Integer salaryMin;

    /** 最高薪资筛选(K) */
    private Integer salaryMax;

    /** 排序:relevance(默认)/latest/salary_desc/salary_asc */
    private String sort;

    /** 页码,默认1 */
    private Integer page;

    /** 每页条数,默认10 */
    private Integer size;
}