package com.hire.user.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hire.model.entity.User;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** Persistence fields kept local to avoid changing teammates' shared model. */
@Getter @Setter
public class UserRecord extends User {
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Override @JsonIgnore
    public String getPassword() { return super.getPassword(); }
}
