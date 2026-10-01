package com.hire.notification.mapper;

import com.hire.model.entity.Notification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationMapper {

    /**
     * 新增通知
     */
    int insert(Notification notification);

    /**
     * 条件查询通知列表（动态条件 + 分页）
     */
    List<Notification> selectList(@Param("userId") Long userId,
                                  @Param("type") String type,
                                  @Param("isRead") Integer isRead,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /**
     * 条件统计通知总数（与 selectList 过滤条件一致，用于分页 total）
     */
    long countList(@Param("userId") Long userId,
                   @Param("type") String type,
                   @Param("isRead") Integer isRead);

    /**
     * 查询未读通知数量
     */
    int countUnread(@Param("userId") Long userId);

    /**
     * 标记单条通知为已读（限定本人通知，防止越权操作他人消息）
     */
    int markAsRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 标记某用户所有通知为已读
     */
    int markAllAsRead(@Param("userId") Long userId);
}
