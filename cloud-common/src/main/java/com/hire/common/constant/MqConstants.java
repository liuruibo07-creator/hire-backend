package com.hire.common.constant;

/**
 * RabbitMQ 常量：交换机、队列、routing key 的命名约定。
 *
 * 生产端（application-service）与消费端（notification-service）共用同一套常量，
 * 避免两边手误导致 exchange / queue 对不上而出现消息路由失败却无报错的情况。
 *
 * 队列拆分说明：
 * 每类事件使用独立的队列 + 独立的 @RabbitListener，避免多个消费者挂在同一队列上
 * 形成竞争消费者（competing consumers），导致消息被 round-robin 分发到错误的处理器。
 */
public interface MqConstants {

    /** 业务主交换机（topic 类型，便于后续扩展其他事件） */
    String EXCHANGE = "hire.topic.exchange";

    /** 投递提交事件专用队列：由 ApplicationSubmittedListener 消费 */
    String QUEUE_SUBMITTED = "hire.notification.submitted.queue";

    /** 投递状态变更事件专用队列：由 ApplicationStatusChangedListener 消费 */
    String QUEUE_STATUS_CHANGED = "hire.notification.status.queue";

    /** 投递成功事件：application-service 发送，notification-service 消费 */
    String ROUTING_KEY_APPLICATION_SUBMITTED = "application.submitted";

    /** 投递状态变更事件：application-service 发送，notification-service 消费（通知求职者） */
    String ROUTING_KEY_APPLICATION_STATUS_CHANGED = "application.status.changed";

    /** 错误交换机：消费端本地重试耗尽后，失败消息经 RepublishMessageRecoverer 重新发布到这里 */
    String ERROR_EXCHANGE = "error.direct";

    /** 错误队列：存放重试耗尽仍失败的毒消息 */
    String ERROR_QUEUE = "error.queue";

    /** 失败消息重新发布到错误交换机使用的 routing key */
    String ROUTING_KEY_ERROR = "error";
}
