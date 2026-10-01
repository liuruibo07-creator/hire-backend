package com.hire.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 职位完整信息 VO：职位详情页与管理员详情接口共用。
 * 字段与 t_job 表一一对应，另含 employerName、categoryName 冗余展示字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 发布者（企业/雇主）用户ID */
    private Long employerId;

    /** 企业名称（Feign 获取，失败降级为空） */
    private String employerName;

    private String title;

    /** 职位类别ID，关联 t_job_category */
    private Long categoryId;

    /** 类别名称 */
    private String categoryName;

    /** 职位描述 */
    private String description;

    /** 职位类型：全职/兼职/实习/合同制 */
    private String jobType;

    private String industry;

    /** 工作城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 经验要求，如 1-3年 */
    private String experienceReq;

    /** 学历要求，如 本科 */
    private String educationReq;

    /** 最低薪资（K/月） */
    private Integer salaryMin;

    /** 最高薪资（K/月） */
    private Integer salaryMax;

    /** 薪资发放月数 */
    private Integer salaryMonths;

    /** 技能要求，逗号分隔 */
    private String skills;

    /** 招聘人数 */
    private Integer headcount;

    /** 状态：0 企业下架，1 招聘中，2 管理员强制下架，3 待审核，4 审核拒绝 */
    private Integer status;

    /** 投递数 */
    private Integer applyCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 审核意见 */
    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
