package com.hire.user.controller;
import com.hire.model.dto.*;
import com.hire.user.model.*;
import com.hire.user.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/user/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service=service; }
    @PostMapping("/register")
    public ApiResult<Long> register(@RequestBody UserRequests.Register dto) {
        return ApiResult.ok("注册成功",service.register(dto));
    }
    @PostMapping("/login")
    public ApiResult<Map<String,Object>> login(@RequestBody UserLoginDTO dto) {
        return ApiResult.ok("登录成功",service.login(dto));
    }
    @GetMapping("/me")
    public ApiResult<Map<String,Object>> me() { return ApiResult.ok("查询成功",service.getCurrentUser()); }
    @PutMapping("/me")
    public ApiResult<Void> update(@RequestBody UserUpdateDTO dto) {
        service.updateCurrentUser(dto); return ApiResult.ok("操作成功",null);
    }
    @PutMapping("/me/password")
    public ApiResult<Void> password(@RequestBody PasswordUpdateDTO dto) {
        service.updatePassword(dto); return ApiResult.ok("操作成功",null);
    }
    @GetMapping("/{id}/info")
    public ApiResult<Map<String,Object>> info(@PathVariable("id") Long id) {
        return ApiResult.ok("查询成功",service.getById(id));
    }
    @GetMapping("/notification-recipients/internal")
    public ApiResult<Map<String,Object>> recipients(@RequestParam(value="afterId",defaultValue="0") long afterId,
                                                   @RequestParam(value="size",defaultValue="500") int size) {
        return ApiResult.ok("查询成功",service.notificationRecipients(afterId,size));
    }
}
