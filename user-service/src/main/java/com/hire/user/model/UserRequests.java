package com.hire.user.model;
import lombok.Data;
public final class UserRequests {
    private UserRequests() {}
    @Data public static class Register {
        private String username;
        private String password;
        private String email;
        private String phone;
        private String role;
        private String realName;
    }
    @Data public static class Status { private Integer status; }
}
