package com.hire.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.application.mapper.OutboxMapper;
import com.hire.common.constant.MqConstants;
import com.hire.model.dto.ApplicationSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {
    OutboxMapper mapper;
    RabbitTemplate rabbit;
    PlatformTransactionManager transactions;
    OutboxDispatcher dispatcher;
    OutboxMessage event;

    @BeforeEach void setup() {
        mapper = mock(OutboxMapper.class);
        rabbit = mock(RabbitTemplate.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        dispatcher = new OutboxDispatcher(mapper, rabbit, new ObjectMapper(), transactions);
        event = new OutboxMessage();
        event.setMessageId("event-1");
        event.setRoutingKey(MqConstants.ROUTING_KEY_APPLICATION_SUBMITTED);
        event.setPayload("{\"messageId\":\"event-1\",\"employerId\":1}");
        when(mapper.lockNext()).thenReturn(event, (OutboxMessage) null);
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            Message outgoing = processor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            org.junit.jupiter.api.Assertions.assertEquals("event-1", outgoing.getMessageProperties().getMessageId());
            org.junit.jupiter.api.Assertions.assertEquals(MessageDeliveryMode.PERSISTENT,
                    outgoing.getMessageProperties().getDeliveryMode());
            CorrelationData data = invocation.getArgument(4);
            if (returned) data.setReturned(new ReturnedMessage(outgoing, 312, "NO_ROUTE", MqConstants.EXCHANGE, event.getRoutingKey()));
            data.getFuture().set(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq(MqConstants.EXCHANGE), eq(event.getRoutingKey()),
                any(ApplicationSubmittedEvent.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test void onlyConfirmedAndRoutedMessagesAreMarkedSent() {
        confirm(true, false);
        when(mapper.markSent("event-1")).thenReturn(1);
        dispatcher.dispatch();
        verify(mapper).markSent("event-1");
        verify(mapper, never()).retry(any(), anyInt(), any());
    }

    @Test void nackRetainsEventWithBackoff() {
        confirm(false, false);
        dispatcher.dispatch();
        verify(mapper).retry(eq("event-1"), eq(5), any());
        verify(mapper, never()).markSent(any());
    }

    @Test void returnedMessageRetriesEvenIfBrokerAckedExchange() {
        confirm(true, true);
        dispatcher.dispatch();
        verify(mapper).retry(eq("event-1"), eq(5), any());
        verify(mapper, never()).markSent(any());
    }

    @Test void connectionFailureRetainsPayloadForRetry() {
        doThrow(new IllegalStateException("offline")).when(rabbit).convertAndSend(anyString(), anyString(),
                any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        event.setAttempts(100);
        dispatcher.dispatch();
        verify(mapper).retry(eq("event-1"), eq(3600), any());
        verify(mapper, never()).markSent(any());
    }

    @Test void databaseFailureRollsBackClaimAndStopsBatch() {
        when(mapper.lockNext()).thenThrow(new IllegalStateException("database offline"));
        dispatcher.dispatch();
        verify(transactions).rollback(any());
        verifyNoInteractions(rabbit);
    }
}
