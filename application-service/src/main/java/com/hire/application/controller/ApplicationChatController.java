package com.hire.application.controller;

import com.hire.application.mapper.ApplicationsMapper;
import com.hire.common.constant.JwtConstant;
import com.hire.common.domain.Result;
import com.hire.common.utils.JwtRequestUtils;
import com.hire.model.entity.Applications;
import com.hire.model.vo.ApplicationChatContextVO;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;

@RestController
@RequestMapping("/applications")
public class ApplicationChatController {
    private final ApplicationsMapper mapper;

    public ApplicationChatController(ApplicationsMapper mapper) { this.mapper = mapper; }

    @GetMapping("/{id}/chat-context/internal")
    public ResponseEntity<Result<ApplicationChatContextVO>> context(
            @PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authorization) {
        Claims claims;
        try {
            claims = JwtRequestUtils.parse(JwtRequestUtils.token(authorization, Collections.emptyList(), false));
        } catch (RuntimeException e) {
            return error(401, "请先登录");
        }
        if (!"employer".equals(claims.get(JwtConstant.ROLE))) return error(403, "仅企业可查询投递聊天关系");
        if (id == null || id <= 0) return error(400, "投递ID不合法");
        Applications application = mapper.selectById(id);
        if (application == null) return error(404, "投递记录不存在");
        if (!Long.valueOf(JwtRequestUtils.userId(claims)).equals(application.getEmployerId())) {
            return error(403, "无权访问该投递");
        }
        return ResponseEntity.ok(Result.success(new ApplicationChatContextVO(application.getId(),
                application.getJobId(), application.getUserId(), application.getEmployerId())));
    }

    private ResponseEntity<Result<ApplicationChatContextVO>> error(int code, String message) {
        return ResponseEntity.status(code).body(Result.error(code, message));
    }
}
