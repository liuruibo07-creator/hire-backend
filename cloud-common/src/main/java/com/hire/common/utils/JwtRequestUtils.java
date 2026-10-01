package com.hire.common.utils;

import com.hire.common.constant.JwtConstant;
import io.jsonwebtoken.Claims;
import java.util.List;

/** Shared by the gateway and chat endpoints; never accepts identity from userId headers. */
public final class JwtRequestUtils {
    public static final String WS_TOKEN_PREFIX = "bearer.";

    private JwtRequestUtils() { }

    public static String token(String authorization, List<String> protocols, boolean websocket) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization.trim();
        }
        if (websocket && protocols != null) {
            String token = null;
            for (String header : protocols) {
                for (String protocol : header.split(",")) {
                    String value = protocol.trim();
                    if (value.startsWith(WS_TOKEN_PREFIX)) {
                        if (token != null) throw new IllegalArgumentException("Multiple WebSocket credentials");
                        token = value.substring(WS_TOKEN_PREFIX.length());
                    }
                }
            }
            return token;
        }
        return null;
    }

    public static Claims parse(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing token");
        Claims claims = JwtUtil.parseJWT(JwtConstant.SECRET_KEY, token);
        userId(claims);
        if (claims.getExpiration() == null) throw new IllegalArgumentException("Missing expiration");
        return claims;
    }

    public static long userId(Claims claims) {
        long id = Long.parseLong(String.valueOf(claims.get(JwtConstant.USER_ID)));
        if (id <= 0) throw new IllegalArgumentException("Invalid user ID");
        return id;
    }
}
