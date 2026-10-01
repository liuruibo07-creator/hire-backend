package com.hire.notification.config;

import com.hire.common.constant.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消费端 MQ 配置：声明交换机、队列、绑定关系与错误处理链路。
 *
 * 队列拆分原则：
 * 每类事件使用独立的队列 + 独立的 @RabbitListener，避免两个消费者挂在同一队列上
 * 形成竞争消费者（competing consumers），导致消息被 round-robin 分发到错误的处理器，
 * 出现"投递提交事件被状态变更监听器消费，status 字段变 null"这类隐蔽 bug。
 *
 * 失败处理采用 RepublishMessageRecoverer（应用层）方案而非死信队列（Broker 层）：
 * 消费失败 -> application.yml 配置的本地重试（retry）耗尽 -> 本恢复器把失败消息
 * 重新发布到错误交换机 -> 错误队列，等人工排查或补偿。
 */
@Configuration
public class NotificationMqConfig {

    /** 主业务交换机 */
    @Bean
    public TopicExchange hireExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.EXCHANGE).durable(true).build();
    }

    /** 投递提交事件专用队列 */
    @Bean
    public Queue submittedQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SUBMITTED).build();
    }

    /** 投递状态变更事件专用队列 */
    @Bean
    public Queue statusChangedQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_STATUS_CHANGED).build();
    }

    /** 绑定：投递提交队列 <- routing key application.submitted */
    @Bean
    public Binding submittedBinding(TopicExchange hireExchange, Queue submittedQueue) {
        return BindingBuilder.bind(submittedQueue)
                .to(hireExchange)
                .with(MqConstants.ROUTING_KEY_APPLICATION_SUBMITTED);
    }

    /** 绑定：状态变更队列 <- routing key application.status.changed */
    @Bean
    public Binding statusChangedBinding(TopicExchange hireExchange, Queue statusChangedQueue) {
        return BindingBuilder.bind(statusChangedQueue)
                .to(hireExchange)
                .with(MqConstants.ROUTING_KEY_APPLICATION_STATUS_CHANGED);
    }

    /** 错误交换机：重试耗尽后，失败消息由 RepublishMessageRecoverer 重新发布到这里 */
    @Bean
    public DirectExchange errorExchange() {
        return ExchangeBuilder.directExchange(MqConstants.ERROR_EXCHANGE).durable(true).build();
    }

    /** 错误队列：存放重试耗尽仍失败的毒消息（poison message），供人工排查/补偿 */
    @Bean
    public Queue errorQueue() {
        return QueueBuilder.durable(MqConstants.ERROR_QUEUE).build();
    }

    /** 错误队列绑定关系 */
    @Bean
    public Binding errorBinding(DirectExchange errorExchange, Queue errorQueue) {
        return BindingBuilder.bind(errorQueue)
                .to(errorExchange)
                .with(MqConstants.ROUTING_KEY_ERROR);
    }

    /**
     * 消息恢复器：必须配合 application.yml 中 listener.simple.retry 启用的本地重试，
     * 否则永远不会被触发。重试耗尽后以 ROUTING_KEY_ERROR 把失败消息发布到错误交换机，
     * 发布成功后原消息被 ack；重发布时会附加 x-exception-* 等错误信息头，便于排查。
     */
    @Bean
    public RepublishMessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, MqConstants.ERROR_EXCHANGE, MqConstants.ROUTING_KEY_ERROR);
    }

    /**
     * 与生产端保持一致的 JSON 转换器。
     * 该 Bean 会被 Spring Boot 自动应用到监听容器，从而把消息反序列化成具体事件对象。
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
