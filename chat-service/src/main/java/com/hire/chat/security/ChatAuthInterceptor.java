package com.hire.chat.security;

import com.hire.model.dto.ChatPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

@Component
public class ChatAuthInterceptor implements HandlerInterceptor {
    private final ChatAuthentication authentication;
    public ChatAuthInterceptor(ChatAuthentication authentication) { this.authentication = authentication; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ChatPrincipal.ATTRIBUTE,
                authentication.authenticate(request.getHeader("Authorization"), Collections.emptyList(), false));
        return true;
    }
}
