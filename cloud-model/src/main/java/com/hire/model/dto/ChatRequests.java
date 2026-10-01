package com.hire.model.dto;

public final class ChatRequests {
    private ChatRequests() { }
    public record Start(Long jobId, Long applicationId) { }
    public record Send(String content) { }
    public record Read(Long throughMessageId) { }
}
