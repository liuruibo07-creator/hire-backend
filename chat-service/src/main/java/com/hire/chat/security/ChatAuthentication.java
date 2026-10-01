package com.hire.chat.security;

import com.hire.chat.exception.ChatException;
import com.hire.model.dto.ChatPrincipal;
import com.hire.chat.service.ChatDirectory;
import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtRequestUtils;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import java.util.List;
import static com.hire.chat.exception.ChatException.require;

@Component
public class ChatAuthentication {
    private final ChatDirectory directory;
    // Feign initializes MVC infrastructure; do not resolve it while MVC/WebSocket auth is being configured.
    public ChatAuthentication(@Lazy ChatDirectory directory) { this.directory = directory; }

    public ChatPrincipal authenticate(String authorization, List<String> protocols, boolean websocket) {
        ChatPrincipal principal;
        try {
            String token = JwtRequestUtils.token(authorization, protocols, websocket);
            Claims claims = JwtRequestUtils.parse(token);
            principal = new ChatPrincipal(JwtRequestUtils.userId(claims),
                    claims.get(JwtConstant.ROLE, String.class), claims.getExpiration().getTime(), token);
        } catch (RuntimeException e) {
            throw new ChatException(401, "未登录或登录已过期");
        }
        validate(principal);
        return principal;
    }

    public void validate(ChatPrincipal principal) {
        require(principal.expiresAt() > System.currentTimeMillis(), 401, "登录已过期");
        require("seeker".equals(principal.role()) || "employer".equals(principal.role()), 403, "仅求职者和企业可使用私信");
        directory.requireRole(principal.userId(), principal.role());
    }
}
