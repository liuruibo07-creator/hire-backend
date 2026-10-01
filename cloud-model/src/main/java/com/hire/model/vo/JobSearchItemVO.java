package com.hire.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 职位搜索结果条目(API设计文档 3.6)
 */
@Data
public class JobSearchItemVO {

    private Long id;

    private String title;

    /** 企业名称(索引冗余字段) */
    private String employerName;

    /** 类别名称(索引冗余字段) */
    private String categoryName;

    private String city;

    private Integer salaryMin;

    private Integer salaryMax;

    private String jobType;

    private String experienceReq;

    private String educationReq;

    private String skills;

    /** 发布时间(yyyy-MM-dd HH:mm:ss) */
    private String createTime;

    /** 高亮片段:字段名 -> 高亮文本列表 */
    private Map<String, List<String>> highlight;
}