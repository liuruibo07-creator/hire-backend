package com.hire.notification.listener;

import com.hire.common.constant.MqConstants;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 投递成功事件监听：收到消息后为职位发布者(企业)生成通知。
 *
 * 队列独立：只监听 QUEUE_SUBMITTED，避免与状态变更监听器形成竞争消费者。
 *
 * 采用自动 ACK + 本地重试（application.yml 中 acknowledge-mode: auto + listener.simple.retry）：
 * - 方法正常返回，容器自动 ack
 * - 抛出异常先在本地重试 3 次（间隔 1s 起指数退避），全部失败后由
 *   RepublishMessageRecoverer 把消息重新发布到错误交换机（error.direct -> error.queue）
 * - 方法内部不要再捕获异常，否则容器会认为处理成功，消息被确认后丢失
 */
@Slf4j
@Component
public class ApplicationSubmittedListener {

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queues = MqConstants.QUEUE_SUBMITTED)
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        log.info("收到投递提交事件, messageId={}, applicationId={}, employerId={}",
                event.getMessageId(), event.getApplicationId(), event.getEmployerId());
        // 为职位发布者生成通知
        notificationService.handleApplicationSubmitted(event);
    }
}
