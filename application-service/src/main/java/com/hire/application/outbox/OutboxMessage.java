package com.hire.application.outbox;

import lombok.Data;

@Data
public class OutboxMessage {
    private String messageId;
    private String routingKey;
    private String payload;
    private String globalXid;
    private int attempts;
}
