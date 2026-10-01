package com.hire.user.controller;
import com.hire.user.model.*;
import com.hire.user.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/user/admin")
public class AdminUserController {
    private final UserService service;
    public AdminUserController(UserService service) { this.service=service; }
    @GetMapping("/users")
    public ApiResult<Map<String,Object>> users(@RequestParam(value="page",defaultValue="1") int page,
            @RequestParam(value="size",defaultValue="10") int size,@RequestParam(value="role",required=false) String role,
            @RequestParam(value="status",required=false) Integer status,@RequestParam(value="keyword",required=false) String keyword) {
        return ApiResult.ok("查询成功",service.listUsers(page,size,role,status,keyword));
    }
    @GetMapping("/users/{id}")
    public ApiResult<Map<String,Object>> detail(@PathVariable("id") Long id) {
        return ApiResult.ok("查询成功",service.adminDetail(id));
    }
    @PutMapping("/users/{id}/status")
    public ApiResult<Map<String,Object>> status(@PathVariable("id") Long id,@RequestBody UserRequests.Status dto) {
        return ApiResult.ok("操作成功",service.updateStatus(id,dto.getStatus()));
    }
    @GetMapping("/statistics")
    public ApiResult<Map<String,Object>> statistics(@RequestParam(value="days",defaultValue="7") int days) {
        return ApiResult.ok("查询成功",service.statistics(days));
    }
}
