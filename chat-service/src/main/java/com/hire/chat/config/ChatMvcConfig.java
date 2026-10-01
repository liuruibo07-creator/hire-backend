package com.hire.chat.config;

import com.hire.chat.security.ChatAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ChatMvcConfig implements WebMvcConfigurer {
    private final ChatAuthInterceptor auth;

    public ChatMvcConfig(ChatAuthInterceptor auth) {
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auth).addPathPatterns("/conversations", "/conversations/**", "/unread-count");
    }
}
