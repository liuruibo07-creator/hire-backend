package com.hire.notification.service;

import com.hire.model.dto.ApplicationStatusChangedEvent;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.dto.NotificationQueryDTO;
import com.hire.model.entity.Notification;
import com.hire.model.vo.PageVO;

public interface NotificationService {

    /**
     * 处理投递提交事件：为职位发布者(企业)生成一条"收到新简历投递"的通知。
     *
     * 失败时直接抛出异常，由监听容器重试耗尽后转入错误队列，不要吞异常。
     *
     * @param event 投递提交事件
     * @throws IllegalArgumentException 事件缺少关键字段，无法处理
     */
    void handleApplicationSubmitted(ApplicationSubmittedEvent event);

    /**
     * 处理投递状态变更事件：为求职者生成一条状态更新通知。
     *
     * @param event 投递状态变更事件
     * @throws IllegalArgumentException 事件缺少关键字段，无法处理
     */
    void handleApplicationStatusChanged(ApplicationStatusChangedEvent event);

    /**
     * 分页查询某用户的通知列表，返回 {total, list} 结构（接口文档 5.1）
     */
    PageVO<Notification> pageNotifications(NotificationQueryDTO queryDTO);

    /**
     * 查询未读通知数量
     */
    int countUnread(Long userId);

    /**
     * 标记单条通知为已读（限定本人通知）
     */
    void markAsRead(Long id, Long userId);

    /**
     * 标记某用户所有通知为已读
     */
    void markAllAsRead(Long userId);
}
