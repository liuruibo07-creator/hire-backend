package com.hire.api.clients;

import com.hire.common.Result;
import com.hire.model.entity.User;
import com.hire.model.vo.UserInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

//value属性是服务名
@FeignClient(value = "user-service")
public interface UserClient {

    @GetMapping("/user/{id}")
    Result<User> queryById(@PathVariable("id") Long id);

    /**
     * 新增用户
     */
       @PostMapping("/user")
    void addUser(@RequestBody User user);

    /**
     * 内部接口:根据用户ID获取用户基本信息(API设计文档 2.9)
     * 供 job-service 获取企业名称、chat-service 校验用户状态等场景调用。
     * 注意:user-service 实际返回 {code:200, message, data} 结构,需按 Result 解包。
     */
    @GetMapping("/api/user/users/{id}/info")
    com.hire.common.domain.Result<UserInfoVO> getUserInfo(@PathVariable("id") Long id);
}
