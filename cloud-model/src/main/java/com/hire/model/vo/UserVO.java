package com.hire.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {
    private String username;
    private String realName;
    private String role;
    private String token; //jwt
}
