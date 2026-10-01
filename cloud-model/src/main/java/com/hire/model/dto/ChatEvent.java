package com.hire.model.dto;

public record ChatEvent(String eventId, String type, String conversationId, Object data) { }
