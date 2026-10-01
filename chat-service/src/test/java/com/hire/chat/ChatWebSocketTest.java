package com.hire.chat;

import com.hire.chat.config.ChatWebSocketConfig;
import com.hire.model.dto.*;
import com.hire.model.vo.ChatViews;
import com.hire.chat.security.ChatAuthentication;
import com.hire.chat.service.ChatDirectory;
import com.hire.chat.websocket.*;
import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes=ChatWebSocketTest.Server.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.cloud.bootstrap.enabled=false","spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false","chat.allowed-origins=http://localhost",
                "spring.datasource.password="})
class ChatWebSocketTest {
    @Configuration
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,RabbitAutoConfiguration.class})
    @Import({ChatWebSocketConfig.class,ChatWebSocketHandler.class,ChatHandshakeInterceptor.class,ChatSessions.class,ChatAuthentication.class})
    static class Server { @Bean ChatDirectory directory() { return mock(ChatDirectory.class); } }
    @LocalServerPort int port;
    @Autowired ChatSessions sessions;
    List<WebSocketSession> opened=new ArrayList<>();
    @AfterEach void close() throws Exception { for(WebSocketSession session:opened) session.close(); }
    String token(long ttl) { return JwtUtil.createJWT(JwtConstant.SECRET_KEY,ttl,new HashMap<>(Map.of("userId",10,"role","seeker"))); }
    WebSocketSession connect(WebSocketHttpHeaders headers,BlockingQueue<String> received) throws Exception {
        WebSocketSession session=new StandardWebSocketClient().doHandshake(new TextWebSocketHandler(){
            @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) { received.add(message.getPayload()); }
        },headers,URI.create("ws://localhost:"+port+"/ws")).get(5,TimeUnit.SECONDS);
        opened.add(session); return session;
    }
    @Test void browserCompatibleSubprotocolHandshakeReceivesEvents() throws Exception {
        WebSocketHttpHeaders headers=new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of("chat.v1","bearer."+token(60000))); headers.setOrigin("http://localhost");
        BlockingQueue<String> received=new LinkedBlockingQueue<>();
        WebSocketSession session=connect(headers,received); assertEquals("chat.v1",session.getAcceptedProtocol());
        sessions.broadcast(new ChatBroadcast(10,20,new ChatEvent("id","conversation.read","1",new ChatViews.ReadPosition("20","3"))));
        String payload=received.poll(5,TimeUnit.SECONDS); assertNotNull(payload); assertTrue(payload.contains("conversation.read"));
    }
    @Test void handshakeRejectsForgedHeadersExpiredTokensAndUnapprovedOrigins() {
        WebSocketHttpHeaders forged=new WebSocketHttpHeaders(); forged.set("userId","10"); forged.set("role","seeker");
        assertThrows(Exception.class,()->connect(forged,new LinkedBlockingQueue<>()));
        WebSocketHttpHeaders expired=new WebSocketHttpHeaders(); expired.setSecWebSocketProtocol(List.of("chat.v1","bearer."+token(-1000)));
        assertThrows(Exception.class,()->connect(expired,new LinkedBlockingQueue<>()));
        WebSocketHttpHeaders origin=new WebSocketHttpHeaders(); origin.setSecWebSocketProtocol(List.of("chat.v1","bearer."+token(60000)));
        origin.setOrigin("http://unapproved.example"); assertThrows(Exception.class,()->connect(origin,new LinkedBlockingQueue<>()));
    }
}
