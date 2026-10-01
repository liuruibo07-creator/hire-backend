package com.hire.notification.controller;

import com.hire.common.context.UserContext;
import com.hire.common.domain.Result;
import com.hire.model.dto.NotificationQueryDTO;
import com.hire.model.entity.Notification;
import com.hire.model.vo.PageVO;
import com.hire.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口，对应《API 接口设计文档》第 5 章。
 *
 * 外部访问路径（前端 /api/notification/notifications/**，经网关 StripPrefix=2 转发后）：
 * - 5.1 GET  /notifications               查询我的通知列表（分页）
 * - 5.2 GET  /notifications/unread-count  查询未读通知数
 * - 5.3 PUT  /notifications/{id}/read     标记单条已读
 * - 5.4 PUT  /notifications/read-all      全部标记已读
 *
 * 当前登录用户取自网关解析 JWT 后透传的 userId 请求头
 * （UserInfoInterceptor 写入 UserContext），前端无需也不允许传 userId，避免越权查询他人通知。
 */
@Slf4j
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /** 5.1 查询我的通知列表：{ page, size, type, isRead }，返回 { total, list } */
    @GetMapping
    public Result<PageVO<Notification>> list(NotificationQueryDTO queryDTO) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(Result.UNAUTHORIZED_CODE, "未登录或登录已过期");
        }
        queryDTO.setUserId(userId);
        return Result.success(notificationService.pageNotifications(queryDTO));
    }

    /** 5.2 查询未读通知数，data 为 Integer */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(Result.UNAUTHORIZED_CODE, "未登录或登录已过期");
        }
        return Result.success(notificationService.countUnread(userId));
    }

    /** 5.3 标记单条通知已读（仅限本人通知） */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(Result.UNAUTHORIZED_CODE, "未登录或登录已过期");
        }
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    /** 5.4 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(Result.UNAUTHORIZED_CODE, "未登录或登录已过期");
        }
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}
