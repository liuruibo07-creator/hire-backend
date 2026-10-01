package com.hire.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 生产端配置。
 *
 * 1. 使用 Jackson2JsonMessageConverter：跨服务用 JSON 传输，避免 JDK 序列化要求两端类名包名完全一致。
 *    消费端（notification-service）也必须注册同类型的 MessageConverter，否则无法反序列化。
 * 2. setMandatory(true) + ReturnsCallback：消息路由不到队列时回调，而不是被静默丢弃。
 * 3. ConfirmCallback：消息是否真正到达 Broker 的异步确认。
 */
@Slf4j
@Configuration
public class MqConfig {

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // TODO 可靠性增强：此处可将消息写入本地消息表，由定时任务补偿重发
                log.error("消息未到达 Broker, correlationData={}, cause={}", correlationData, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned ->
                log.error("消息路由失败, exchange={}, routingKey={}, replyCode={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(),
                        returned.getReplyCode(), returned.getReplyText()));

        return rabbitTemplate;
    }
}
