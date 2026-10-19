package com.hire.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.application.mapper.OutboxMapper;
import io.seata.core.context.RootContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationOutbox {
    private final OutboxMapper mapper;
    private final ObjectMapper json;

    public ApplicationOutbox(OutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String id, String routingKey, Object event) {
        OutboxMessage message = new OutboxMessage();
        message.setMessageId(id);
        message.setRoutingKey(routingKey);
        message.setGlobalXid(RootContext.getXID());
        try {
            message.setPayload(json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize application event", e);
        }
        if (mapper.insert(message) != 1) throw new IllegalStateException("Cannot persist application event");
    }
}
