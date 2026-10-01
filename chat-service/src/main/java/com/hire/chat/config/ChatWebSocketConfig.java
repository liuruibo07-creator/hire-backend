package com.hire.chat.config;

import com.hire.chat.websocket.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor authentication;
    private final String[] origins;

    public ChatWebSocketConfig(ChatWebSocketHandler handler, ChatHandshakeInterceptor authentication,
                               @Value("${chat.allowed-origins:}") String origins) {
        this.handler = handler;
        this.authentication = authentication;
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").addInterceptors(authentication).setAllowedOrigins(origins);
    }
}
