package com.hire.model.dto;

/**
 * 聊天未读提醒任务载荷。
 *
 * 由 chat-service 在消息发出后，作为 20 分钟延迟消息的 data 字段重新广播给自身。
 * 到期后消费端检查接收方是否仍未读该消息，仍未读则给在线的接收方推一条提醒。
 *
 * 字段使用基本类型 long（而非 Long + ToStringSerializer）：
 * 延迟消息不经过前端，仅在 chat-service 内部往返，ID 用数值即可，
 * 避免序列化成字符串后消费端取值需多一步解析。
 */
public record ChatUnreadReminder(long messageId, long senderId, long recipientId,
                                 String preview, String jobTitle) { }
