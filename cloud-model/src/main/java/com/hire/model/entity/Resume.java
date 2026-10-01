package com.hire.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 简历实体：与 t_resume 表字段一一对应
 * 用于持久层（Mapper）、Service 与 Controller 对外返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 简历主键
     */
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 简历标题
     */
    private String title;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别：0 女，1 男
     */
    private Integer gender;

    /**
     * 出生年份
     */
    private Integer birthYear;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 学历
     */
    private String education;

    /**
     * 毕业院校
     */
    private String university;

    /**
     * 专业
     */
    private String major;

    /**
     * 毕业日期
     */
    private String graduationDate;

    /**
     * 工作经历
     */
    private String workExperience;

    /**
     * 技能
     */
    private String skills;

    /**
     * 期望职位
     */
    private String expectedPosition;

    /**
     * 期望薪资
     */
    private String expectedSalary;

    /**
     * 期望城市
     */
    private String expectedCity;

    /**
     * 自我评价
     */
    private String selfEvaluation;

    /**
     * 是否默认：0 否，1 是
     */
    private Integer isDefault;

    /**
     * 是否删除：0 未删除，1 已删除（逻辑删除标志）
     */
    private Integer deleted;
}
