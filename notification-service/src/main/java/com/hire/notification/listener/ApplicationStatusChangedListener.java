package com.hire.notification.listener;

import com.hire.common.constant.MqConstants;
import com.hire.model.dto.ApplicationStatusChangedEvent;
import com.hire.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 投递状态变更事件监听：收到消息后为求职者生成状态更新通知。
 *
 * 队列独立：只监听 QUEUE_STATUS_CHANGED，避免与投递提交监听器形成竞争消费者。
 *
 * 失败处理策略与投递提交监听器保持一致：
 * auto ACK + 本地重试，重试耗尽后由 RepublishMessageRecoverer 转入错误队列，
 * 方法内部不要捕获异常，否则消息被确认后丢失。
 */
@Slf4j
@Component
public class ApplicationStatusChangedListener {

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queues = MqConstants.QUEUE_STATUS_CHANGED)
    public void onApplicationStatusChanged(ApplicationStatusChangedEvent event) {
        log.info("收到投递状态变更事件, messageId={}, applicationId={}, status={}",
                event.getMessageId(), event.getApplicationId(), event.getStatus());
        // 为求职者生成状态更新通知
        notificationService.handleApplicationStatusChanged(event);
    }
}
