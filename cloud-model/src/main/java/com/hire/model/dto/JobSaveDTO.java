package com.hire.model.dto;

import lombok.Data;

/**
 * 职位发布/编辑请求参数(API设计文档 3.1)
 */
@Data
public class JobSaveDTO {

    /** 职位名称(必填) */
    private String title;

    /** 职位类别ID */
    private Long categoryId;

    /** 职位描述 */
    private String description;

    /** 职位类型:全职/兼职/实习/合同制(必填) */
    private String jobType;

    /** 行业 */
    private String industry;

    /** 工作城市(必填) */
    private String city;

    /** 详细地址 */
    private String address;

    /** 经验要求,默认"不限" */
    private String experienceReq;

    /** 学历要求,默认"不限" */
    private String educationReq;

    /** 最低薪资(K) */
    private Integer salaryMin;

    /** 最高薪资(K) */
    private Integer salaryMax;

    /** 薪资月数,默认12 */
    private Integer salaryMonths;

    /** 技能要求,逗号分隔 */
    private String skills;

    /** 招聘人数,默认1 */
    private Integer headcount;
}