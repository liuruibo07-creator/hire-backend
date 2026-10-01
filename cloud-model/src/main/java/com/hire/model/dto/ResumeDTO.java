package com.hire.model.dto;

import lombok.Data;

@Data
public class ResumeDTO {
    /**
     * 简历主键，新增时为空，更新时必填
     */
    private Long id;

    /**
     * 所属用户，由服务端从登录态注入，不信任前端传值
     */
    private Long userId;

    private String title;

    private String name;

    private Integer gender;

    private Integer birthYear;

    private String phone;

    private String email;

    private String education;


    private String university;


    private String major;


    private String graduationDate;


    private String workExperience;


    private String skills;


    private String expectedPosition;


    private String expectedSalary;

    private String expectedCity;


    private String selfEvaluation;


    private Integer isDefault;

    /**
     * 是否删除：0 未删除，1 已删除（逻辑删除标志）
     */
    private Integer deleted;
}
