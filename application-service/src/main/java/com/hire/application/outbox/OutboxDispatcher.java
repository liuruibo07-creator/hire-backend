package com.hire.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.application.mapper.OutboxMapper;
import com.hire.common.constant.MqConstants;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.dto.ApplicationStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;

/** At-least-once delivery. A crash after broker ACK is handled by the consumer's durable receipt. */
@Component
@Slf4j
public class OutboxDispatcher {
    private final OutboxMapper mapper;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public OutboxDispatcher(OutboxMapper mapper, RabbitTemplate rabbit, ObjectMapper json,
                            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.rabbit = rabbit;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${hire.outbox.delay-ms:5000}")
    public void dispatch() {
        for (int i = 0; i < 20; i++) {
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> dispatchOne()))) return;
            } catch (RuntimeException e) {
                // Database unavailable/commit uncertain: retain the row for a later attempt.
                log.error("Outbox transaction failed; delivery will be retried", e);
                return;
            }
        }
    }

    private boolean dispatchOne() {
        OutboxMessage message = mapper.lockNext();
        if (message == null) return false;
        try {
            Class<?> type;
            if (MqConstants.ROUTING_KEY_APPLICATION_SUBMITTED.equals(message.getRoutingKey())) {
                type = ApplicationSubmittedEvent.class;
            } else if (MqConstants.ROUTING_KEY_APPLICATION_STATUS_CHANGED.equals(message.getRoutingKey())) {
                type = ApplicationStatusChangedEvent.class;
            } else {
                throw new IllegalArgumentException("Unsupported outbox event type");
            }
            Object event = json.readValue(message.getPayload(), type);
            CorrelationData confirmation = new CorrelationData(message.getMessageId());
            rabbit.convertAndSend(MqConstants.EXCHANGE, message.getRoutingKey(), event, outgoing -> {
                outgoing.getMessageProperties().setMessageId(message.getMessageId());
                outgoing.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return outgoing;
            }, confirmation);
            CorrelationData.Confirm result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.isAck() || confirmation.getReturned() != null) {
                throw new IllegalStateException("Broker rejected or returned event");
            }
            if (mapper.markSent(message.getMessageId()) != 1) throw new IllegalStateException("Outbox row missing");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            int seconds = (int) Math.min(3600L, 5L << Math.min(message.getAttempts(), 10));
            // Do not store payloads, credentials or unbounded exception messages in the retry table.
            mapper.retry(message.getMessageId(), seconds, e.getClass().getSimpleName());
            log.warn("Outbox delivery deferred, messageId={}, attempt={}", message.getMessageId(), message.getAttempts() + 1);
        }
        return true;
    }
}
