package com.hire.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration
public class ChatMqConfig {
    public static final String EXCHANGE = "hire.chat.events";

    /** 事件类型：消息发出 N 分钟后对方仍未读时的提醒（仅走延迟通道，不实时广播） */
    public static final String TYPE_UNREAD_REMINDER = "message.unread.reminder";

    /**
     * 延迟消息交换机（方案A：rabbitmq_delayed_message_exchange 插件）。
     *
     * type 使用 x-delayed-message，实际路由语义由 x-delayed-type=fanout 决定，
     * 与原来的 FanoutExchange 广播行为完全一致：
     * - 消息不带 x-delay 头：立即投递（现有 publish() 路径不受影响）
     * - 消息带 x-delay 头：插件暂存，到期后按 fanout 广播给所有绑定队列
     *
     * 注意：交换机 type 在 Broker 中不可变，若 Broker 上已存在旧的 fanout 版
     * hire.chat.events，启动声明会报 PRECONDITION_FAILED，需先删除旧交换机
     * （管理台删除，或 rabbitmqadmin delete exchange name=hire.chat.events）。
     * 队列与绑定由本配置自动重建，无需手工处理。
     */
    @Bean
    public CustomExchange chatExchange() {
        return new CustomExchange(EXCHANGE, "x-delayed-message", true, false,
                Map.of("x-delayed-type", "fanout"));
    }

    // One exclusive auto-delete queue PER INSTANCE, never a shared competing-consumer queue.
    @Bean
    public AnonymousQueue chatInstanceQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding chatBinding(CustomExchange chatExchange, AnonymousQueue chatInstanceQueue) {
        // BindingBuilder 对 CustomExchange 的泛型重载在 Spring AMQP 2.x 下返回值不兼容，
        // 直接用 Binding 构造函数声明：fanout 语义 routing key 为空串
        return new Binding(chatInstanceQueue.getName(), Binding.DestinationType.QUEUE,
                chatExchange.getName(), "", null);
    }

    @Bean
    public Jackson2JsonMessageConverter chatMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper, "com.hire.model.dto");
    }
}
