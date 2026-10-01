package com.hire.user.controller;
import com.hire.common.Result;
import com.hire.model.entity.User;
import com.hire.user.model.UserRequests;
import com.hire.user.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/** Compatibility for the existing shared UserClient; new consumers use documented URLs. */
@RestController
@RequestMapping("/user")
public class LegacyUserController {
    private final UserService service;
    public LegacyUserController(UserService service) { this.service=service; }
    @GetMapping("/{id}")
    public Result<User> get(@PathVariable("id") Long id) {
        Map<String,Object> info=service.getById(id);
        User user=new User();
        user.setId((Long)info.get("id")); user.setUsername((String)info.get("username"));
        user.setRealName((String)info.get("realName")); user.setRole((String)info.get("role"));
        user.setAvatar((String)info.get("avatar"));
        return Result.success(user);
    }
    @PostMapping
    public Result<Void> add(@RequestBody UserRequests.Register dto) {
        service.register(dto); return Result.success();
    }
}
