package com.hire.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.chat.config.ChatMqConfig;
import com.hire.chat.exception.ChatException;
import com.hire.chat.mapper.ChatMapper;
import com.hire.model.dto.*;
import com.hire.model.vo.ChatViews;
import com.hire.chat.security.ChatAuthentication;
import com.hire.chat.service.ChatEventRelay;
import com.hire.chat.websocket.ChatSessions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.web.socket.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatRealtimeTest {
    WebSocketSession session(String id,long userId,long expiry) {
        WebSocketSession session=mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id); when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(ChatPrincipal.ATTRIBUTE,new ChatPrincipal(userId,userId==20?"employer":"seeker",expiry,"test")));
        return session;
    }
    ChatBroadcast event() {
        return new ChatBroadcast(10,20,new ChatEvent("event-1","conversation.read","1",new ChatViews.ReadPosition("20","3")));
    }
    @Test void brokerRoundtripReachesBothInstancesAndOnlyConversationParticipants() throws Exception {
        ChatMqConfig topology=new ChatMqConfig();
        assertNotEquals(topology.chatInstanceQueue().getName(),topology.chatInstanceQueue().getName());
        assertTrue(topology.chatInstanceQueue().isExclusive()); assertTrue(topology.chatInstanceQueue().isAutoDelete());
        ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        Jackson2JsonMessageConverter converter=topology.chatMessageConverter(json);
        ChatBroadcast received=(ChatBroadcast)converter.fromMessage(converter.toMessage(event(),new MessageProperties()));
        ChatAuthentication auth=mock(ChatAuthentication.class);
        ChatSessions nodeA=new ChatSessions(json,auth),nodeB=new ChatSessions(json,auth);
        WebSocketSession sender=session("sender",10,Long.MAX_VALUE),recipient=session("recipient",20,Long.MAX_VALUE),
                other=session("other",30,Long.MAX_VALUE),secondDevice=session("second",20,Long.MAX_VALUE);
        nodeA.add(sender); nodeA.add(other); nodeB.add(recipient); nodeB.add(secondDevice);
        new ChatEventRelay(mock(RabbitTemplate.class),nodeA,mock(ChatMapper.class),json).receive(received);
        new ChatEventRelay(mock(RabbitTemplate.class),nodeB,mock(ChatMapper.class),json).receive(received);
        verify(sender).sendMessage(any(TextMessage.class)); verify(recipient).sendMessage(any(TextMessage.class));
        verify(secondDevice).sendMessage(any(TextMessage.class)); verify(other,never()).sendMessage(any());
        nodeA.shutdown(); nodeB.shutdown();
    }
    @Test void expiredOrDisabledConnectionsDoNotReceivePrivateEvents() throws Exception {
        ChatAuthentication auth=mock(ChatAuthentication.class); ChatSessions sessions=new ChatSessions(new ObjectMapper(),auth);
        WebSocketSession expired=session("expired",10,1),disabled=session("disabled",20,Long.MAX_VALUE);
        doThrow(new ChatException(403,"disabled")).when(auth).validate(argThat(p->p.userId()==20));
        sessions.add(expired); sessions.add(disabled); sessions.broadcast(event());
        verify(expired,never()).sendMessage(any()); verify(disabled,never()).sendMessage(any());
        verify(expired).close(any()); verify(disabled).close(any());
    }
}
