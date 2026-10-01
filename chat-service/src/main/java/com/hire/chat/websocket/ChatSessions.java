package com.hire.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.model.dto.ChatBroadcast;
import com.hire.model.dto.ChatEvent;
import com.hire.model.dto.ChatPrincipal;
import com.hire.chat.security.ChatAuthentication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class ChatSessions {
    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final ChatAuthentication authentication;

    public ChatSessions(ObjectMapper json, ChatAuthentication authentication) {
        this.json = json; this.authentication = authentication;
    }

    public void add(WebSocketSession session) throws IOException {
        ChatPrincipal principal = (ChatPrincipal) session.getAttributes().get(ChatPrincipal.ATTRIBUTE);
        if (principal == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        connections.put(session.getId(), new Connection(principal,
                new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024)));
    }

    public void remove(String id) { connections.remove(id); }

    public void pong(String id) {
        Connection connection = connections.get(id);
        if (connection != null) connection.lastPong = System.currentTimeMillis();
    }

    /** 指定用户在当前实例是否持有有效 WebSocket 连接（用于延迟提醒在多实例间只命中在线方）。 */
    public boolean isOnline(long userId) {
        for (Connection connection : connections.values()) {
            if (connection.principal.userId() == userId) return true;
        }
        return false;
    }

    /**
     * 向单个用户的所有连接推送一条事件（仅匹配该 userId 的会话）。
     * 用于"未读提醒"这类只针对接收方、而非会话双方广播的场景。
     */
    public void sendToUser(long userId, ChatEvent event) {
        TextMessage message;
        try { message = new TextMessage(json.writeValueAsString(event)); }
        catch (JsonProcessingException e) {
            log.error("聊天事件序列化失败，eventId={}", event.eventId());
            return;
        }
        Map<Long, Boolean> valid = new HashMap<>();
        for (Connection connection : connections.values()) {
            if (connection.principal.userId() == userId) {
                if (valid(connection, valid)) send(connection, message);
                else close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    public void broadcast(ChatBroadcast broadcast) {
        TextMessage message;
        try { message = new TextMessage(json.writeValueAsString(broadcast.event())); }
        catch (JsonProcessingException e) {
            log.error("聊天事件序列化失败，eventId={}", broadcast.event().eventId());
            return;
        }
        Map<Long, Boolean> valid = new HashMap<>();
        for (Connection connection : connections.values()) {
            long userId = connection.principal.userId();
            if (userId == broadcast.seekerId() || userId == broadcast.employerId()) {
                if (valid(connection, valid)) send(connection, message);
                else close(connection, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void heartbeat() {
        Map<Long, Boolean> valid = new HashMap<>();
        for (Connection connection : connections.values()) {
            if (System.currentTimeMillis() - connection.lastPong > 90000 || !valid(connection, valid)) {
                close(connection, CloseStatus.POLICY_VIOLATION);
            } else send(connection, new PingMessage());
        }
    }

    private boolean valid(Connection connection, Map<Long, Boolean> valid) {
        if (connection.principal.expiresAt() <= System.currentTimeMillis()) return false;
        return valid.computeIfAbsent(connection.principal.userId(), id -> {
            try { authentication.validate(connection.principal); return true; }
            catch (RuntimeException e) { return false; }
        });
    }

    private void send(Connection connection, WebSocketMessage<?> message) {
        try {
            if (connection.session.isOpen()) connection.session.sendMessage(message);
            else connections.remove(connection.session.getId(), connection);
        } catch (Exception e) {
            close(connection, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void close(Connection connection, CloseStatus status) {
        connections.remove(connection.session.getId(), connection);
        try { connection.session.close(status); }
        catch (IOException e) { log.debug("关闭聊天连接失败，sessionId={}", connection.session.getId()); }
    }

    @PreDestroy public void shutdown() {
        for (Connection connection : connections.values()) close(connection, CloseStatus.GOING_AWAY);
    }

    private static final class Connection {
        private final ChatPrincipal principal;
        private final WebSocketSession session;
        private volatile long lastPong = System.currentTimeMillis();
        private Connection(ChatPrincipal principal, WebSocketSession session) {
            this.principal = principal; this.session = session;
        }
    }
}
