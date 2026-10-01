package com.hire.chat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.List;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private final ChatSessions sessions;
    public ChatWebSocketHandler(ChatSessions sessions) { this.sessions = sessions; }
    @Override public List<String> getSubProtocols() { return List.of("chat.v1"); }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception { sessions.add(session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override protected void handlePongMessage(WebSocketSession session, PongMessage message) { sessions.pong(session.getId()); }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION.withReason("Use REST to send messages"));
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session.getId()); session.close(CloseStatus.SERVER_ERROR);
    }
}
