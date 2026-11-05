package com.aicontractmanager.chatbot.dto;

import com.aicontractmanager.chatbot.domain.ChatMessage;
import java.util.UUID;

public record ChatReply(UUID sessionId, ChatMessage message) {
}
