package com.hire.chat.controller;

import com.hire.chat.service.ChatService;
import com.hire.common.domain.Result;
import com.hire.model.dto.*;
import com.hire.model.entity.ChatConversation;
import com.hire.model.entity.ChatMessage;
import com.hire.model.vo.ChatViews;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/conversations")
    public Result<ChatConversation> start(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor,
                                          @RequestBody ChatRequests.Start request) {
        return Result.success(service.start(actor, request));
    }

    @GetMapping("/conversations")
    public Result<ChatViews.Conversations> list(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor,
                                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(actor, page, size));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<ChatViews.Messages> history(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor,
                                              @PathVariable Long id, @RequestParam(required = false) Long beforeId,
                                              @RequestParam(required = false) Long afterId, @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.history(actor, id, beforeId, afterId, size));
    }

    @PostMapping("/conversations/{id}/messages")
    public Result<ChatMessage> send(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor,
                                    @PathVariable Long id, @RequestBody ChatRequests.Send request) {
        return Result.success(service.send(actor, id, request));
    }

    @PutMapping("/conversations/{id}/read")
    public Result<ChatViews.ReadPosition> read(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor,
                                               @PathVariable Long id, @RequestBody ChatRequests.Read request) {
        return Result.success(service.read(actor, id, request));
    }

    @GetMapping("/unread-count")
    public Result<Long> unread(@RequestAttribute(ChatPrincipal.ATTRIBUTE) ChatPrincipal actor) {
        return Result.success(service.unread(actor));
    }
}
