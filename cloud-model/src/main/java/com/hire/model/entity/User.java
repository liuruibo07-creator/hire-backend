package com.hire.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String username;
    private String password;
    private String realName; //真实姓名
    private String email;
    private String phone;
    private String avatar;
    private String role; //角色，数据库默认 seeker
}