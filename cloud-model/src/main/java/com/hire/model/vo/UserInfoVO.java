package com.hire.model.vo;

import lombok.Data;

/**
 * 用户基本信息(API设计文档 2.8 内部 Feign 接口返回)
 */
@Data
public class UserInfoVO {

    private Long id;

    private String username;

    /** 真实姓名/企业名称 */
    private String realName;

    /** 角色:seeker-求职者 / employer-企业 / admin-管理员 */
    private String role;

    /** 账号状态:1-正常 / 0-停用 */
    private Integer status;

    private String avatar;
}