package com.hire.notification.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hire.common.utils.RedisUtil;
import com.hire.common.utils.SnowflakeIdWorker;
import com.hire.model.dto.ApplicationStatusChangedEvent;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.dto.NotificationQueryDTO;
import com.hire.model.entity.Notification;
import com.hire.model.vo.PageVO;
import com.hire.notification.mapper.NotificationMapper;
import com.hire.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 通知落库服务。
 *
 * 消费端为 auto ACK + 本地重试模式：本方法不捕获任何异常，失败一律向上抛出，
 * 重试耗尽后由 RepublishMessageRecoverer 转入错误队列；
 * 千万不要用 try-catch 吞掉异常后 return，那会被视为处理成功，消息被 ack 后丢失。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private RedisUtil redisUtil;

    /** Redis 键前缀:用户未读通知数(notify:unread:{userId}) */
    private static final String KEY_UNREAD = "notify:unread:";
    /** 未读数缓存 TTL:30 分钟兜底,正常由"标记已读"主动删除 */
    private static final Duration UNREAD_TTL = Duration.ofMinutes(30);

    /**
     * 投递提交事件处理：为职位发布者(企业)生成"收到新简历投递"通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleApplicationSubmitted(ApplicationSubmittedEvent event) {
        if (event == null || StrUtil.isBlank(event.getMessageId()) || event.getEmployerId() == null) {
            // 缺少关键字段的消息永远无法处理成功，重试多少次都一样，
            // 必须抛异常让重试耗尽后转入错误队列，由人工介入；不能静默 return
            log.error("投递事件缺少关键字段, event={}", event);
            throw new IllegalArgumentException("投递事件缺少关键字段: " + event);
        }

        String jobTitle = StrUtil.isBlank(event.getJobTitle()) ? "" : event.getJobTitle();
        String content = String.format("有求职者投递了职位《%s》，请及时查看处理。", jobTitle);

        Notification notification = Notification.builder()
                .id(SnowflakeIdWorker.nextSnowflakeId())
                .userId(event.getEmployerId())
                .type("application_submitted")
                .title("收到新的简历投递")
                .content(content)
                .relatedId(event.getApplicationId())
                .isRead(0)   // 0=未读
                .deleted(0)
                .build();

        int rows = notificationMapper.insert(notification);
        if (rows == 0) {
            log.warn("通知插入失败, messageId={}", event.getMessageId());
        } else {
            // 未读计数+1(仅插入成功时计数,重复消息不会走到这里)
            Long unread = redisUtil.increment(KEY_UNREAD + event.getEmployerId());
            if (unread != null && unread == 1L) {
                redisUtil.expire(KEY_UNREAD + event.getEmployerId(), UNREAD_TTL);
            }
            log.info("通知已生成, messageId={}, employerId={}, applicationId={}",
                    event.getMessageId(), event.getEmployerId(), event.getApplicationId());
        }
    }

    /**
     * 投递状态变更事件处理：为求职者生成状态更新通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleApplicationStatusChanged(ApplicationStatusChangedEvent event) {
        if (event == null || StrUtil.isBlank(event.getMessageId())
                || event.getApplicantId() == null || event.getStatus() == null) {
            log.error("投递状态变更事件缺少关键字段, event={}", event);
            throw new IllegalArgumentException("投递状态变更事件缺少关键字段: " + event);
        }

        String jobTitle = StrUtil.isBlank(event.getJobTitle()) ? "" : event.getJobTitle();
        String statusText = statusText(event.getStatus());
        StringBuilder content = new StringBuilder();
        content.append(String.format("您投递的职位《%s》状态更新为：%s。", jobTitle, statusText));
        if (StrUtil.isNotBlank(event.getRemark())) {
            content.append("企业备注：").append(event.getRemark());
        }

        Notification notification = Notification.builder()
                .id(SnowflakeIdWorker.nextSnowflakeId())
                .userId(event.getApplicantId())
                .type("application_status_changed")
                .title("投递状态更新")
                .content(content.toString())
                .relatedId(event.getApplicationId())
                .isRead(0)   // 0=未读
                .deleted(0)
                .build();

        int rows = notificationMapper.insert(notification);
        if (rows == 0) {
            log.warn("状态变更通知插入失败, messageId={}", event.getMessageId());
        } else {
            // 未读计数+1(仅插入成功时计数)
            Long unread = redisUtil.increment(KEY_UNREAD + event.getApplicantId());
            if (unread != null && unread == 1L) {
                redisUtil.expire(KEY_UNREAD + event.getApplicantId(), UNREAD_TTL);
            }
            log.info("状态变更通知已生成, messageId={}, applicantId={}, status={}",
                    event.getMessageId(), event.getApplicantId(), event.getStatus());
        }
    }

    /**
     * 分页查询通知列表：先 count 再查列表，count 为 0 时跳过列表查询省一次 SQL
     */
    @Override
    public PageVO<Notification> pageNotifications(NotificationQueryDTO queryDTO) {
        int page = queryDTO.getPage() == null || queryDTO.getPage() < 1 ? 1 : queryDTO.getPage();
        int size = queryDTO.getSize() == null || queryDTO.getSize() < 1 ? 10 : Math.min(queryDTO.getSize(), 100);
        int offset = (page - 1) * size;

        long total = notificationMapper.countList(
                queryDTO.getUserId(), queryDTO.getType(), queryDTO.getIsRead());
        List<Notification> list = total == 0
                ? Collections.emptyList()
                : notificationMapper.selectList(
                        queryDTO.getUserId(), queryDTO.getType(), queryDTO.getIsRead(),
                        offset, size);
        return new PageVO<>(total, list);
    }

    @Override
    public int countUnread(Long userId) {
        // 未读数缓存(Cache Aside):命中直接返回,未命中回源并回填;Redis 异常时 RedisUtil 已降级
        Long cached = redisUtil.getLong(KEY_UNREAD + userId);
        if (cached != null) {
            return cached.intValue();
        }
        int count = notificationMapper.countUnread(userId);
        redisUtil.set(KEY_UNREAD + userId, String.valueOf(count), UNREAD_TTL);
        return count;
    }

    @Override
    public void markAsRead(Long id, Long userId) {
        notificationMapper.markAsRead(id, userId);
        // 未读数已变化,删除缓存,下次查询回源重新计算
        redisUtil.delete(KEY_UNREAD + userId);
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
        redisUtil.delete(KEY_UNREAD + userId);
    }

    /**
     * 投递状态 -> 中文描述：1-已查看，2-面试邀请，3-已录用，4-已拒绝
     */
    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 1: return "已查看";
            case 2: return "面试邀请";
            case 3: return "已录用";
            case 4: return "已拒绝";
            default: return "未知(" + status + ")";
        }
    }
}
