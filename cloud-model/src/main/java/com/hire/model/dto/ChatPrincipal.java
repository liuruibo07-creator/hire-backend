package com.hire.model.dto;

/** Stored in request/session attributes, never serialized or logged. */
public record ChatPrincipal(long userId, String role, long expiresAt, String token) {
    public static final String ATTRIBUTE = "chatPrincipal";
    @Override public String toString() { return "ChatPrincipal[userId=" + userId + ", role=" + role + "]"; }
}
