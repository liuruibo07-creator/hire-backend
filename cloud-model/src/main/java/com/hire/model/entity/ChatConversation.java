package com.hire.model.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatConversation {
    @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long jobId;
    @JsonSerialize(using = ToStringSerializer.class) private Long seekerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long employerId;
    private String jobTitle;
    @JsonSerialize(using = ToStringSerializer.class) private Long lastMessageId;
    @JsonSerialize(using = ToStringSerializer.class) private Long seekerReadId;
    @JsonSerialize(using = ToStringSerializer.class) private Long employerReadId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String lastMessageContent;
    private long unreadCount;
}
