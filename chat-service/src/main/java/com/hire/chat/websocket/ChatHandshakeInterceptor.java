package com.hire.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.chat.exception.ChatException;
import com.hire.model.dto.ChatPrincipal;
import com.hire.chat.security.ChatAuthentication;
import com.hire.common.domain.Result;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    private final ChatAuthentication authentication;
    private final ObjectMapper json;
    public ChatHandshakeInterceptor(ChatAuthentication authentication, ObjectMapper json) {
        this.authentication = authentication; this.json = json;
    }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) throws Exception {
        try {
            attributes.put(ChatPrincipal.ATTRIBUTE, authentication.authenticate(
                    request.getHeaders().getFirst("Authorization"), request.getHeaders().get("Sec-WebSocket-Protocol"), true));
            return true;
        } catch (ChatException e) {
            response.setStatusCode(HttpStatus.valueOf(e.getCode()));
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            json.writeValue(response.getBody(), Result.error(e.getCode(), e.getMessage()));
            return false;
        }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }
}
