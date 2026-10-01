package com.hire.model.vo;

import com.hire.model.entity.ChatConversation;
import com.hire.model.entity.ChatMessage;
import java.util.List;

public final class ChatViews {
    private ChatViews() { }
    public record Conversations(long total, List<ChatConversation> list) { }
    public record Messages(List<ChatMessage> list, boolean hasMore, String nextBeforeId, String nextAfterId) { }
    public record ReadPosition(String userId, String throughMessageId) { }
}
