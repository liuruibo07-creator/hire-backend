package com.hire.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 职位实体：与 t_job 表字段一一对应（job-service 使用）
 * 字段以数据库设计文档 6.1 为准
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 发布者（企业/雇主）用户ID */
    private Long employerId;

    private String title;

    /** 职位类别ID，关联 t_job_category */
    private Long categoryId;

    private String description;

    /** 职位类型：全职/兼职/实习 */
    private String jobType;

    private String industry;

    private String city;

    private String address;

    /** 经验要求，如 1-3年 */
    private String experienceReq;

    /** 学历要求，如 本科 */
    private String educationReq;

    private Integer salaryMin;

    private Integer salaryMax;

    /** 薪资发放月数，如 13 */
    private Integer salaryMonths;

    private String skills;

    /** 招聘人数 */
    private Integer headcount;

    /** 状态：0 企业下架，1 招聘中，2 管理员强制下架，3 待审核，4 审核拒绝 */
    private Integer status;

    /** 浏览数 */
    private Integer viewCount;

    /** 投递数 */
    private Integer applyCount;

    /** 审核意见(拒绝时必填) */
    private String reviewRemark;

    /** 审核管理员ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 是否删除：0 未删除，1 已删除 */
    private Integer deleted;
}
