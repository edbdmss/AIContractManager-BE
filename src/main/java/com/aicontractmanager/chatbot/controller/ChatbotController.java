package com.aicontractmanager.chatbot.controller;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import com.aicontractmanager.chatbot.domain.ChatSession;
import com.aicontractmanager.chatbot.dto.ChatMessageRequest;
import com.aicontractmanager.chatbot.dto.ChatReply;
import com.aicontractmanager.chatbot.dto.CreateChatSessionRequest;
import com.aicontractmanager.chatbot.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aicontract/chat/sessions")
public class ChatbotController {
    private final ChatbotService service;

    public ChatbotController(ChatbotService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChatSession> listSessions(Principal principal) {
        return service.listSessions(userId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSession createSession(Principal principal,
                                     @RequestBody(required = false) CreateChatSessionRequest request) {
        return service.createSession(userId(principal), request == null ? null : request.contractId());
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> listMessages(Principal principal, @PathVariable UUID sessionId) {
        return service.listMessages(userId(principal), sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    public ChatReply send(Principal principal, @PathVariable UUID sessionId,
                          @Valid @RequestBody ChatMessageRequest request) {
        return service.send(userId(principal), sessionId, request);
    }

    private UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
