package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
 * JobVO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobVO {
    private Long id;
    private Long employerId;
    private String employerName;
    private String title;
    private Long categoryId;
    private String categoryName;
    private String description;
    private String jobType;
    private String industry;
    private String city;
    private String experienceReq;
    private String educationReq;
    private Integer salaryMin;
    private Integer salaryMax;
    private String skills;
    private Integer headcount;
    private Integer status;
    private Integer viewCount;
    private Integer applyCount;
    private LocalDateTime createTime;
}
