package com.hire.chat.service;

import com.hire.chat.config.ChatMqConfig;
import com.hire.chat.mapper.ChatMapper;
import com.hire.model.dto.ChatBroadcast;
import com.hire.model.dto.ChatEvent;
import com.hire.model.dto.ChatUnreadReminder;
import com.hire.chat.websocket.ChatSessions;
import com.hire.model.entity.ChatConversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@Slf4j
public class ChatEventRelay {
    /** 消息发出 20 分钟后若对方仍未读，则推送一条未读提醒 */
    private static final long UNREAD_REMINDER_DELAY_MS = 20 * 60 * 1000L;

    private final RabbitTemplate rabbit;
    private final ChatSessions sessions;
    private final ChatMapper chatMapper;
    private final ObjectMapper objectMapper;
    public ChatEventRelay(RabbitTemplate rabbit, ChatSessions sessions,
                         ChatMapper chatMapper, ObjectMapper objectMapper) {
        this.rabbit = rabbit; this.sessions = sessions;
        this.chatMapper = chatMapper; this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatBroadcast broadcast) {
        // 未读提醒走延迟通道（scheduleUnreadReminder），不在此实时广播
        if (ChatMqConfig.TYPE_UNREAD_REMINDER.equals(broadcast.event().type())) return;
        try { rabbit.convertAndSend(ChatMqConfig.EXCHANGE, "", broadcast); }
        catch (RuntimeException e) {
            // The message already committed. A failed push must not turn a successful send into a retry/duplicate.
            log.warn("聊天实时推送失败，可从历史补齐：eventId={}, conversationId={}",
                    broadcast.event().eventId(), broadcast.event().conversationId());
        }
    }

    /**
     * 未读提醒事件：事务提交后才进入延迟交换机，20 分钟后由插件投递回各实例队列。
     * 与 publish() 互斥：同一事件只走其中一条路径，避免提醒被即时广播。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleUnreadReminder(ChatBroadcast broadcast) {
        if (!ChatMqConfig.TYPE_UNREAD_REMINDER.equals(broadcast.event().type())) return;
        publishDelayed(broadcast, UNREAD_REMINDER_DELAY_MS);
    }

    /**
     * 延迟广播（方案A：rabbitmq_delayed_message_exchange 插件）。
     *
     * 在消息头写入 x-delay，由交换机插件暂存，到期后才 fanout 广播到各实例队列。
     * 交换机为 x-delayed-message 类型（见 ChatMqConfig.chatExchange），Broker 需已安装
     * rabbitmq_delayed_message_exchange 插件，否则声明/发送会失败。
     *
     * @param delayMs 延迟毫秒数，插件上限约 2^32-1 毫秒（约 49 天）
     */
    public void publishDelayed(ChatBroadcast broadcast, long delayMs) {
        try {
            rabbit.convertAndSend(ChatMqConfig.EXCHANGE, "", broadcast, message -> {
                // Spring AMQP 2.x 只有 setDelay(int)；int 毫秒上限约 24.8 天，业务延迟远小于该值，强转安全
                message.getMessageProperties().setDelay((int) delayMs);
                return message;
            });
        } catch (RuntimeException e) {
            // 与 publish() 一致：消息体已随事务落库，推送失败只记日志，可从历史补齐
            log.warn("聊天延迟推送失败，可从历史补齐：eventId={}, conversationId={}, delayMs={}",
                    broadcast.event().eventId(), broadcast.event().conversationId(), delayMs);
        }
    }

    @RabbitListener(queues = "#{chatInstanceQueue.name}")
    public void receive(ChatBroadcast broadcast) {
        // 未读提醒需先查库判断是否仍未读，再决定是否给接收方定向推送
        if (ChatMqConfig.TYPE_UNREAD_REMINDER.equals(broadcast.event().type())) {
            handleUnreadReminder(broadcast);
        } else {
            sessions.broadcast(broadcast);
        }
    }

    /**
     * 处理到期的未读提醒：仅当接收方在线且仍未读该消息时，给接收方定向推送一条提醒事件。
     *
     * 多实例语义：延迟交换机 fanout 到所有实例队列，每条实例各自判断"接收方是否在我这里在线"，
     * 只有持有接收方 WebSocket 连接的实例会真正查库并发送，其余实例 isOnline 直接跳过，避免无谓 DB 查询。
     */
    private void handleUnreadReminder(ChatBroadcast broadcast) {
        ChatUnreadReminder reminder;
        try {
            // data 经 MQ 往返后是 LinkedHashMap，convertValue 还原成强类型
            reminder = objectMapper.convertValue(broadcast.event().data(), ChatUnreadReminder.class);
        } catch (RuntimeException e) {
            log.warn("未读提醒载荷解析失败，eventId={}", broadcast.event().eventId(), e);
            return;
        }
        // 接收方不在线本实例：提醒无意义（其重连后可从未读数补齐），直接跳过，省一次 DB 查询
        if (!sessions.isOnline(reminder.recipientId())) return;

        ChatConversation conversation;
        try {
            conversation = chatMapper.find(Long.parseLong(broadcast.event().conversationId()));
        } catch (RuntimeException e) {
            log.warn("未读提醒查询会话失败，conversationId={}", broadcast.event().conversationId(), e);
            return;
        }
        if (conversation == null) return;

        // 接收方已读位置 >= 该消息 id 视为已读，跳过提醒
        long readId = reminder.senderId() == conversation.getSeekerId()
                ? conversation.getEmployerReadId() : conversation.getSeekerReadId();
        if (readId >= reminder.messageId()) return;

        ChatEvent reminderEvent = new ChatEvent(
                java.util.UUID.randomUUID().toString(),
                ChatMqConfig.TYPE_UNREAD_REMINDER,
                broadcast.event().conversationId(),
                reminder);
        sessions.sendToUser(reminder.recipientId(), reminderEvent);
    }
}
