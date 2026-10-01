package com.hire.model.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long conversationId;
    @JsonSerialize(using = ToStringSerializer.class) private Long senderId;
    private String content;
    private LocalDateTime createTime;
}
